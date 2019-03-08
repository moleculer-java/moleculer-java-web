/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2019 Andras Berkes [andras.berkes@programmer.net]<br>
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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.context.CallOptions;
import services.moleculer.eventbus.Matcher;
import services.moleculer.web.middleware.HttpMiddleware;
import services.moleculer.web.template.AbstractTemplateEngine;

public class Route {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(Route.class);

	// --- PARENT BROKER ---

	protected final ServiceBroker broker;

	// --- PROPERTIES ---

	protected final String path;
	protected final MappingPolicy mappingPolicy;
	protected final CallOptions.Options opts;
	protected final String[] whitelist;
	protected final Alias[] aliases;
	protected final AbstractTemplateEngine abstractTemplateEngine;

	// --- ROUTE-SPECIFIC MIDDLEWARES ---
	
	protected final HashSet<HttpMiddleware> routeMiddlewares = new HashSet<>(32);
	
	// --- CONSTRUCTORS ---

	public Route(ServiceBroker broker, String path, MappingPolicy mappingPolicy, CallOptions.Options opts,
			String[] whitelist, Alias[] aliases) {
		this(broker, path, mappingPolicy, opts, whitelist, aliases, null);
	}
	
	public Route(ServiceBroker broker, String path, MappingPolicy mappingPolicy, CallOptions.Options opts,
			String[] whitelist, Alias[] aliases, AbstractTemplateEngine abstractTemplateEngine) {
		
		this.broker = Objects.requireNonNull(broker);
		this.path = formatPath(path);
		this.mappingPolicy = mappingPolicy;
		this.opts = opts;
		this.abstractTemplateEngine = abstractTemplateEngine;
		
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
							opts, abstractTemplateEngine);
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
					Mapping mapping = new Mapping(broker, httpMethod, this.path + pattern, actionName, opts, abstractTemplateEngine);
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
			Mapping mapping = new Mapping(broker, httpMethod, pattern, actionName, opts, abstractTemplateEngine);
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

	public void started(ServiceBroker broker, HashSet<HttpMiddleware> globalMiddlewares) throws Exception {

		// Start middlewares
		for (HttpMiddleware middleware : routeMiddlewares) {
			if (!globalMiddlewares.contains(middleware)) {
				middleware.started(broker);
			}
		}
	}

	// --- STOP MIDDLEWARES ---

	public void stopped(HashSet<HttpMiddleware> globalMiddlewares) {

		// Stop middlewares
		for (HttpMiddleware middleware : routeMiddlewares) {
			if (!globalMiddlewares.contains(middleware)) {
				try {
					middleware.stopped();
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
		tree.putObject("whitelist", whitelist);
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

}