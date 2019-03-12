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

	protected String path = "";
	protected MappingPolicy mappingPolicy = MappingPolicy.RESTRICT;
	protected CallOptions.Options opts;
	protected String[] whiteList;
	protected Alias[] aliases;

	// --- PARENT BROKER ---

	protected ServiceBroker broker;

	// --- CUSTOM PRE/POST PROCESSORS ---

	protected CallProcessor beforeCall;
	protected CallProcessor afterCall;

	// --- TEMPLATE ENGINE ---

	protected AbstractTemplateEngine templateEngine;

	// --- ROUTE-SPECIFIC MIDDLEWARES ---

	protected final Set<HttpMiddleware> routeMiddlewares = new LinkedHashSet<>(32);

	// --- CONSTRUCTORS ---

	public Route() {
	}

	public Route(String path, HttpMiddleware... middlewares) {
		setPath(path);
		use(middlewares);
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
		if (whiteList != null && whiteList.length > 0) {
			for (String pattern : whiteList) {
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

	public Route use(HttpMiddleware... middlewares) {
		if (middlewares != null && middlewares.length > 0) {
			use(Arrays.asList(middlewares));
		}

		// Return this (for method chaining)
		return this;
	}

	public Route use(Collection<HttpMiddleware> middlewares) {
		if (middlewares != null) {
			routeMiddlewares.addAll(middlewares);
		}

		// Return this (for method chaining)
		return this;
	}

	// --- START MIDDLEWARES ---

	public void started(ServiceBroker broker, Set<HttpMiddleware> globalMiddlewares) throws Exception {

		// Set pointer of parent broker
		this.broker = broker;

		// Start middlewares
		for (HttpMiddleware middleware : routeMiddlewares) {
			if (!globalMiddlewares.contains(middleware)) {
				middleware.started(broker);
				String p = path == null || path.isEmpty() ? p = "/" : path;
				logger.info(nameOf(middleware, false) + " middleware started on route \"" + p + "\".");
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
						String p = path == null || path.isEmpty() ? p = "/" : path;
						logger.info(nameOf(middleware, false) + " middleware stopped on route \"" + p + "\".");
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
		if (whiteList != null) {
			tree.putObject("whiteList", whiteList);
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

	// --- ADD ALIAS ---

	public Route addAlias(String pathPattern, String actionName) {
		return addAlias(Alias.ALL, pathPattern, actionName);
	}

	public Route addAlias(String httpMethod, String pathPattern, String actionName) {
		return addAlias(new Alias(httpMethod, pathPattern, actionName));
	}

	public Route addAlias(Alias... aliases) {
		if (aliases == null || aliases.length == 0) {
			return this;
		}
		LinkedList<Alias> list = new LinkedList<>();
		if (this.aliases != null) {
			list.addAll(Arrays.asList(this.aliases));
		}
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

		// Return this (for method chaining)
		return this;
	}

	// --- ADD TO WHITE LIST ---

	public Route addToWhiteList(String... whiteListEntries) {
		if (whiteListEntries == null || whiteListEntries.length == 0) {
			return this;
		}
		LinkedList<String> list = new LinkedList<>();
		if (whiteList != null) {
			list.addAll(Arrays.asList(whiteList));
		}
		for (String whiteListEntry : whiteListEntries) {
			if (whiteListEntry != null && !whiteListEntry.isEmpty()) {
				if (whiteListEntry.indexOf('.') == -1) {
					whiteListEntry = whiteListEntry + "*";
				}
				if (!whiteListEntry.startsWith("/")) {
					whiteListEntry = "/" + whiteListEntry;
				}
				list.addLast(whiteListEntry);
			}
		}

		whiteList = new String[list.size()];
		list.toArray(whiteList);

		// Return this (for method chaining)
		return this;
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

	public String[] getWhiteList() {
		if (whiteList == null) {
			return null;
		}
		String[] copy = new String[whiteList.length];
		System.arraycopy(whiteList, 0, copy, 0, copy.length);
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

	public void setPath(String path) {
		this.path = formatPath(path);
	}

	public void setMappingPolicy(MappingPolicy mappingPolicy) {
		this.mappingPolicy = mappingPolicy;
	}

	public void setOpts(CallOptions.Options opts) {
		this.opts = opts;
	}

	public void setWhiteList(String... whiteList) {
		this.whiteList = null;
		if (whiteList != null && whiteList.length > 0) {
			for (String whiteListEntry : whiteList) {
				addToWhiteList(whiteListEntry);
			}
		}
	}

	public void setAliases(Alias... aliases) {
		this.aliases = null;
		if (aliases != null && aliases.length > 0) {
			for (Alias alias : aliases) {
				addAlias(alias);
			}
		}
	}

}