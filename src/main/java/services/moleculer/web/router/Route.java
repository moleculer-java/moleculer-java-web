/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2017 Andras Berkes [andras.berkes@programmer.net]<br>
 * Based on Moleculer Framework for NodeJS [https://moleculer.services].
 * <br><br>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:<br>
 * <br>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.<br>
 * <br>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package services.moleculer.web.router;

import static services.moleculer.util.CommonUtils.formatPath;
import static services.moleculer.util.CommonUtils.nameOf;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.context.CallOptions;
import services.moleculer.eventbus.Matcher;
import services.moleculer.web.CallProcessor;
import services.moleculer.web.middleware.HttpMiddleware;
import services.moleculer.web.template.AbstractTemplateEngine;

public class Route {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(Route.class);

	// --- PROPERTIES ---

	protected final String path;
	protected final MappingPolicy mappingPolicy;
	protected final CallOptions.Options opts;
	protected final String[] whitelist;
	protected final Alias[] aliases;

	// --- PARENT BROKER ---

	protected ServiceBroker broker;

	// --- CUSTOM PRE/POST PROCESSORS ---

	protected CallProcessor beforeCall;
	protected CallProcessor afterCall;

	// --- TEMPLATE ENGINE ---

	protected AbstractTemplateEngine templateEngine;

	// --- ROUTE-SPECIFIC MIDDLEWARES ---

	protected final Set<HttpMiddleware> routeMiddlewares = new LinkedHashSet<>(32);

	// --- CONSTRUCTOR ---

	public Route(String path, MappingPolicy mappingPolicy, CallOptions.Options opts, String[] whitelist,
			Alias[] aliases) {
		this.path = formatPath(path);
		this.mappingPolicy = mappingPolicy;
		this.opts = opts;

		if (whitelist != null && whitelist.length > 0) {
			for (int i = 0; i < whitelist.length; i++) {
				whitelist[i] = formatPath(whitelist[i]);
			}
		}
		this.whitelist = whitelist;
		if (aliases != null && aliases.length > 0) {
			LinkedList<Alias> list = new LinkedList<>();
			for (Alias alias : aliases) {
				if (Alias.REST.endsWith(alias.httpMethod)) {
					list.addLast(new Alias(Alias.GET, alias.pathPattern, alias.actionName + ".find"));
					list.addLast(new Alias(Alias.GET, alias.pathPattern + "/:id", alias.actionName + ".get"));
					list.addLast(new Alias(Alias.POST, alias.pathPattern, alias.actionName + ".create"));
					list.addLast(new Alias(Alias.PUT, alias.pathPattern + "/:id", alias.actionName + ".update"));
					list.addLast(new Alias(Alias.DELETE, alias.pathPattern + "/:id", alias.actionName + ".remove"));
				} else {
					list.addLast(alias);
				}
			}
			this.aliases = new Alias[list.size()];
			list.toArray(this.aliases);
		} else {
			this.aliases = null;
		}
	}

	// --- REQUEST PROCESSOR ---

	public Mapping findMapping(String httpMethod, String path) {
		if (this.path != null && !this.path.isEmpty()) {
			if (!path.startsWith(this.path)) {
				return null;
			}
		}
		String shortPath = path.substring(this.path.length());
		if (aliases != null && aliases.length > 0) {
			for (Alias alias : aliases) {
				if (Alias.ALL.equals(alias.httpMethod) || httpMethod.equals(alias.httpMethod)) {
					Mapping mapping = new Mapping(broker, httpMethod, this.path + alias.pathPattern, alias.actionName,
							opts, templateEngine, this, beforeCall, afterCall);
					if (mapping.matches(httpMethod, path)) {
						if (!routeMiddlewares.isEmpty()) {
							mapping.use(routeMiddlewares);
						}
						return mapping;
					}
				}
			}
		}
		String actionName = shortPath.replace('/', '.').replace('~', '$');
		while (actionName.startsWith(".")) {
			actionName = actionName.substring(1);
		}
		if (whitelist != null && whitelist.length > 0) {
			for (String pattern : whitelist) {
				if (Matcher.matches(shortPath, pattern)) {
					Mapping mapping = new Mapping(broker, httpMethod, this.path + pattern, actionName, opts,
							templateEngine, this, beforeCall, afterCall);
					if (!routeMiddlewares.isEmpty()) {
						mapping.use(routeMiddlewares);
					}
					return mapping;
				}
			}
		}
		if (mappingPolicy == MappingPolicy.ALL) {
			String pattern;
			if (this.path == null || this.path.isEmpty()) {
				pattern = path;
			} else {
				pattern = this.path + '*';
			}
			Mapping mapping = new Mapping(broker, httpMethod, pattern, actionName, opts, templateEngine, this,
					beforeCall, afterCall);
			if (!routeMiddlewares.isEmpty()) {
				mapping.use(routeMiddlewares);
			}
			return mapping;
		}
		return null;
	}

	// --- ADD MIDDLEWARES TO ROUTE ---

	public void use(HttpMiddleware... middlewares) {
		use(Arrays.asList(middlewares));
	}

	public void use(Collection<HttpMiddleware> middlewares) {
		routeMiddlewares.addAll(middlewares);
	}

	// --- START MIDDLEWARES ---

	public void started(ServiceBroker broker, Set<HttpMiddleware> globalMiddlewares) throws Exception {

		// Set pointer of parent broker
		this.broker = broker;

		// Start middlewares
		for (HttpMiddleware middleware : routeMiddlewares) {
			if (!globalMiddlewares.contains(middleware)) {
				middleware.started(broker);
				logger.info(nameOf(middleware, true) + " middleware started on route \"" + path + "\".");
			}
		}
	}

	// --- STOP MIDDLEWARES ---

	public void stopped(Set<HttpMiddleware> globalMiddlewares, boolean debug) {

		// Stop middlewares
		for (HttpMiddleware middleware : routeMiddlewares) {
			if (!globalMiddlewares.contains(middleware)) {
				try {
					middleware.stopped();
					if (debug) {
						logger.info(nameOf(middleware, true) + " middleware stopped on route \"" + path + "\".");
					}
				} catch (Exception ignored) {
					logger.warn("Unable to stop middleware!");
				}
			}
		}
	}

	// --- CONVERT TO TREE ---

	public Tree toTree() {
		Tree tree = new Tree();
		tree.put("path", path);
		if (whitelist != null) {
			tree.putObject("whitelist", whitelist);
		}
		if (opts != null) {
			Tree o = tree.putMap("opts");
			o.put("nodeID", opts.nodeID);
			o.put("retryCount", opts.retryCount);
			o.put("timeout", opts.timeout);
		}
		if (aliases != null) {
			Tree as = tree.putList("aliases");
			for (Alias alias : aliases) {
				Tree a = as.addMap();
				a.put("httpMethod", alias.httpMethod);
				a.put("pathPattern", alias.pathPattern);
				a.put("actionName", alias.actionName);
			}
		}
		return tree;
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public void setTemplateEngine(AbstractTemplateEngine templateEngine) {
		this.templateEngine = templateEngine;
	}

	public AbstractTemplateEngine getTemplateEngine() {
		return templateEngine;
	}

	public CallProcessor getBeforeCall() {
		return beforeCall;
	}

	public void setBeforeCall(CallProcessor beforeCall) {
		this.beforeCall = beforeCall;
	}

	public CallProcessor getAfterCall() {
		return afterCall;
	}

	public void setAfterCall(CallProcessor afterCall) {
		this.afterCall = afterCall;
	}

	// --- READ-ONLY PROPERTY GETTERS ---
	
	public String getPath() {
		return path;
	}

	public HttpMiddleware[] getMiddlewares() {
		HttpMiddleware[] array = new HttpMiddleware[routeMiddlewares.size()];
		routeMiddlewares.toArray(array);
		return array;
	}

	public ServiceBroker getBroker() {
		return broker;
	}

	public MappingPolicy getMappingPolicy() {
		return mappingPolicy;
	}

	public CallOptions.Options getOpts() {
		return opts;
	}

	public String[] getWhitelist() {
		if (whitelist == null) {
			return null;
		}
		String[] copy = new String[whitelist.length];
		System.arraycopy(whitelist, 0, copy, 0, copy.length);
		return copy;
	}

	public Alias[] getAliases() {
		if (aliases == null) {
			return null;
		}
		Alias[] copy = new Alias[aliases.length];
		System.arraycopy(aliases, 0, copy, 0, copy.length);
		return copy;
	}

}