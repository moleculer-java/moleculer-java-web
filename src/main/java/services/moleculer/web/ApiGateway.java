/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2018 Andras Berkes [andras.berkes@programmer.net]<br>
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
package services.moleculer.web;

import static services.moleculer.util.CommonUtils.nameOf;
import static services.moleculer.web.common.HttpConstants.CONTENT_LENGTH;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import services.moleculer.ServiceBroker;
import services.moleculer.context.CallOptions;
import services.moleculer.service.Service;
import services.moleculer.web.middleware.HttpMiddleware;
import services.moleculer.web.middleware.NotFound;
import services.moleculer.web.router.Alias;
import services.moleculer.web.router.Mapping;
import services.moleculer.web.router.MappingPolicy;
import services.moleculer.web.router.Route;
import services.moleculer.web.template.AbstractTemplateEngine;

public class ApiGateway extends Service implements RequestProcessor {

	// --- ROUTES ---

	protected Route[] routes = new Route[0];

	/**
	 * Last route (for the last middleware)
	 */
	protected Route lastRoute;

	/**
	 * Last middleware (custom error pages, HTTP-redirector, etc.)
	 */
	protected HttpMiddleware lastMiddleware = new NotFound();

	// --- PROPERTIES ---

	/**
	 * Print more debug messages.
	 */
	protected boolean debug;

	/**
	 * Maximum number of cached routes.
	 */
	protected int cachedRoutes = 1024;

	// --- VARIABLES ---

	/**
	 * Static mappings.
	 */
	protected LinkedHashMap<String, Mapping> staticMappings;

	/**
	 * Dynamic mappings.
	 */
	protected final LinkedList<Mapping> dynamicMappings = new LinkedList<>();

	/**
	 * Global middlewares.
	 */
	protected HashSet<HttpMiddleware> globalMiddlewares = new HashSet<>(32);

	// --- TEMPLATE ENGINE ---

	/**
	 * HTML template engine.
	 */
	protected AbstractTemplateEngine abstractTemplateEngine;

	// --- LOCKS ---

	protected final Lock readLock;
	protected final Lock writeLock;

	// --- CONSTRUCTOR ---

	public ApiGateway() {

		// Init locks
		ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);
		readLock = lock.readLock();
		writeLock = lock.writeLock();
	}

	// --- START GATEWAY INSTANCE ---

	/**
	 * Initializes gateway instance.
	 *
	 * @param broker
	 *            parent ServiceBroker
	 * @param config
	 *            optional configuration of the current component
	 */
	@Override
	public void started(ServiceBroker broker) throws Exception {
		super.started(broker);

		// Static mappings
		staticMappings = new LinkedHashMap<String, Mapping>((cachedRoutes + 1) * 2) {

			private static final long serialVersionUID = 2994447707758047152L;

			protected final boolean removeEldestEntry(Map.Entry<String, Mapping> entry) {
				if (this.size() > cachedRoutes) {
					return true;
				}
				return false;
			};

		};

		// Start global middlewares
		for (HttpMiddleware middleware : globalMiddlewares) {
			middleware.started(broker);
			if (debug) {
				logger.info(nameOf(middleware, true) + " global middleware started.");
			}
		}

		// Start routes (annd route-specific middlewares)
		for (Route route : routes) {
			route.started(broker, globalMiddlewares);
			if (debug) {
				logger.info("Route installed:\r\n" + route.toTree());
			}
		}

		// Set last route (ServeStatic, "404 Not Found", etc.)
		lastRoute = new Route(broker, "", MappingPolicy.ALL, null, null, null, abstractTemplateEngine);
		lastRoute.use(lastMiddleware);
	}

	// --- STOP GATEWAY INSTANCE ---

	/**
	 * Closes gateway.
	 */
	@Override
	public void stopped() {

		// Stop middlewares
		for (HttpMiddleware middleware : globalMiddlewares) {
			try {
				middleware.stopped();
				if (debug) {
					logger.info(nameOf(middleware, true) + " middleware stopped.");
				}
			} catch (Exception ignored) {
				logger.warn("Unable to stop middleware!");
			}
		}

		// Stop routes (and route-specific middlewares)
		for (Route route : routes) {
			route.stopped(globalMiddlewares);
		}
		setRoutes(new Route[0]);

		// Clear middleware registry
		writeLock.lock();
		try {
			globalMiddlewares.clear();
		} finally {
			writeLock.unlock();
		}
		logger.info("ApiGateway server stopped.");
	}

	// --- PROCESS (SERVLET OR NETTY) HTTP REQUEST ---

	/**
	 * Handles request of the HTTP client.
	 * 
	 * @param req
	 *            WebRequest object that contains the request the client made of
	 *            the ApiGateway
	 * @param rsp
	 *            WebResponse object that contains the response the ApiGateway
	 *            returns to the client
	 * 
	 * @throws Exception
	 *             if an input or output error occurs while the ApiGateway is
	 *             handling the HTTP request
	 */
	@Override
	public void service(WebRequest req, WebResponse rsp) throws Exception {

		// Try to find in static mappings (eg. "/user")
		String httpMethod = req.getMethod();
		String path = req.getPath();
		String staticKey = httpMethod + '|' + path;
		Mapping mapping;
		readLock.lock();
		try {
			mapping = staticMappings.get(staticKey);
			if (mapping == null) {

				// Try to find in dynamic mappings (eg. "/user/:id")
				for (Mapping dynamicMapping : dynamicMappings) {
					if (dynamicMapping.matches(httpMethod, path)) {
						if (debug) {
							logger.info(httpMethod + ' ' + path + " found in dyncmic mapping cache.");
						}
						mapping = dynamicMapping;
						break;
					}
				}
			} else if (debug) {
				logger.info(httpMethod + ' ' + path + " found in static mapping cache (key: " + staticKey + ").");
			}
		} finally {
			readLock.unlock();
		}

		// Invoke cached mapping
		if (mapping != null) {
			mapping.service(req, rsp);
			return;
		}

		// Find in routes
		for (Route route : routes) {
			mapping = route.findMapping(httpMethod, path);
			if (mapping != null) {
				if (debug) {
					logger.info("New mapping created by the following route:\r\n" + route.toTree());
				}
				if (!globalMiddlewares.isEmpty()) {
					mapping.use(globalMiddlewares);
				}
				break;
			}
		}

		// Store new mapping in cache
		if (mapping != null) {
			writeLock.lock();
			try {
				if (mapping.isStatic()) {
					staticMappings.put(staticKey, mapping);
					if (debug) {
						logger.info("New mapping stored in the static mapping cache (key: " + staticKey + ").");
					}
				} else {
					dynamicMappings.addLast(mapping);
					if (dynamicMappings.size() > cachedRoutes) {
						dynamicMappings.removeFirst();
					}
					if (debug) {
						logger.info("New mapping stored in the dynamic mapping cache.");
					}
				}
			} finally {
				writeLock.unlock();
			}

			// Invoke new (and cached) mapping
			if (mapping != null) {
				mapping.service(req, rsp);
				return;
			}
		}

		// Find in "lastRoute" (~=executes NotFound middleware)
		if (debug) {
			logger.info("Mapping not found, invoking default middlewares...");
		}
		mapping = lastRoute.findMapping(httpMethod, path);
		if (mapping != null) {
			if (!globalMiddlewares.isEmpty()) {
				mapping.use(globalMiddlewares);
			}
			mapping.service(req, rsp);
			return;
		}

		// 404 Not Found (this does not happen, "lastRoute" will execute)
		if (debug) {
			logger.info("Mapping not found for request: " + path);
		}
		rsp.setStatus(404);
		rsp.setHeader(CONTENT_LENGTH, "0");
		rsp.end();
	}

	// --- GLOBAL MIDDLEWARES ---

	public void use(HttpMiddleware... middlewares) {
		use(Arrays.asList(middlewares));
	}

	public void use(Collection<HttpMiddleware> middlewares) {
		LinkedList<HttpMiddleware> newMiddlewares = new LinkedList<>();
		for (HttpMiddleware middleware : middlewares) {
			if (globalMiddlewares.add(middleware)) {
				newMiddlewares.addLast(middleware);
			}
		}
		if (!newMiddlewares.isEmpty()) {
			readLock.lock();
			try {
				if (staticMappings != null) {
					for (Mapping mapping : staticMappings.values()) {
						mapping.use(newMiddlewares);
					}
				}
				for (Mapping mapping : dynamicMappings) {
					mapping.use(newMiddlewares);
				}
			} finally {
				readLock.unlock();
			}
		}
	}

	// --- ADD ROUTE ---

	/**
	 * Define a route for a list of Services with the specified path prefix (eg.
	 * if the path is "/rest-services" and service list is "service1", the
	 * service's "func" action will available on
	 * "http://host:port/rest-services/service1/func").
	 *
	 * @param path
	 *            path prefix for all enumerated services (eg. "/rest-services")
	 * @param serviceList
	 *            list of services (eg. "service1,service2,service3")
	 * @param middlewares
	 *            optional middlewares (eg. CorsHeaders)
	 *            
	 * @return route the new route
	 */
	public Route addRoute(String path, String serviceList, HttpMiddleware... middlewares) {
		String[] serviceNames = serviceList.split(",");
		LinkedList<String> list = new LinkedList<>();
		for (String serviceName : serviceNames) {
			serviceName = '/' + serviceName.trim() + "*";
			if (serviceName.length() > 2) {
				list.addLast(serviceName);
			}
		}
		String[] whiteList = new String[list.size()];
		list.toArray(whiteList);
		Route route = new Route(broker, path, MappingPolicy.RESTRICT, null, whiteList, null, abstractTemplateEngine);
		if (middlewares != null && middlewares.length > 0) {
			route.use(middlewares);
		}
		return addRoute(route);
	}

	/**
	 * Define a route to a single action. The action will available on the
	 * specified path.
	 *
	 * @param httpMethod
	 *            HTTP method (eg. "GET", "POST", "ALL", "REST", etc.)
	 * @param path
	 *            path of the action (eg. "numbers/add" creates an endpoint on
	 *            "http://host:port/numbers/add")
	 * @param actionName
	 *            name of action (eg. "math.add")
	 * @param middlewares
	 *            optional middlewares (eg. CorsHeaders)
	 *            
	 * @return route the new route
	 */
	public Route addRoute(String httpMethod, String path, String actionName, HttpMiddleware... middlewares) {
		Alias alias = new Alias(httpMethod, path, actionName);
		Route route = new Route(broker, "", MappingPolicy.RESTRICT, null, null, new Alias[] { alias }, abstractTemplateEngine);
		if (middlewares != null && middlewares.length > 0) {
			route.use(middlewares);
		}
		return addRoute(route);
	}

	/**
	 * Define a route.
	 * 
	 * @param path
	 *            path of the action (eg. "numbers/add" creates an endpoint on
	 *            "http://host:port/numbers/add")
	 * @param mappingPolicy
	 *            MappingPolicy of this Route
	 * @param opts
	 *            CallOptions of this Route (timeout, retry count, nodeID)
	 * @param whitelist
	 *            optional whitelist
	 * @param aliases
	 *            optional aliases
	 * 
	 * @return route the new route
	 */
	public Route addRoute(String path, MappingPolicy mappingPolicy, CallOptions.Options opts, String[] whitelist,
			Alias[] aliases) {
		return addRoute(new Route(broker, path, mappingPolicy, opts, whitelist, aliases, abstractTemplateEngine));
	}

	/**
	 * Adds a route to the list of routes.
	 *
	 * @param route
	 *            the new route
	 *            
	 * @return route the new route
	 */
	public Route addRoute(Route route) {
		writeLock.lock();
		try {
			Route[] copy = new Route[routes.length + 1];
			System.arraycopy(routes, 0, copy, 0, routes.length);
			copy[routes.length] = route;
			routes = copy;
			if (staticMappings != null) {
				staticMappings.clear();
			}
			dynamicMappings.clear();
		} finally {
			writeLock.unlock();
		}
		return route;
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public Route[] getRoutes() {
		return routes;
	}

	public void setRoutes(Route[] routes) {
		this.routes = Objects.requireNonNull(routes);
		writeLock.lock();
		try {
			if (staticMappings != null) {
				staticMappings.clear();
			}
			dynamicMappings.clear();
		} finally {
			writeLock.unlock();
		}
	}

	public int getCachedRoutes() {
		return cachedRoutes;
	}

	public void setCachedRoutes(int cacheSize) {
		this.cachedRoutes = cacheSize;
	}

	public HttpMiddleware getLastMiddleware() {
		return lastMiddleware;
	}

	public void setLastMiddleware(HttpMiddleware lastMiddleware) {
		this.lastMiddleware = lastMiddleware;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public AbstractTemplateEngine getTemplateEngine() {
		return abstractTemplateEngine;
	}

	public void setTemplateEngine(AbstractTemplateEngine abstractTemplateEngine) {
		this.abstractTemplateEngine = abstractTemplateEngine;
	}

}