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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.context.CallOptions;
import services.moleculer.service.Action;
import services.moleculer.service.Service;
import services.moleculer.web.middleware.HttpMiddleware;
import services.moleculer.web.middleware.NotFound;
import services.moleculer.web.middleware.ServeStatic;
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
	protected Set<HttpMiddleware> globalMiddlewares = new LinkedHashSet<>(32);

	// --- TEMPLATE ENGINE ---

	/**
	 * HTML template engine.
	 */
	protected AbstractTemplateEngine templateEngine;

	// --- WEBSOCKET REGISTRY ---

	/**
	 * WebSocket registry (Netty or J2EE)
	 */
	protected WebSocketRegistry webSocketRegistry;

	// --- CUSTOM PRE/POST PROCESSORS ---

	/**
	 * Custom message pre-processor.
	 */
	protected CallProcessor beforeCall;

	/**
	 * Custom message post-processor.
	 */
	protected CallProcessor afterCall;

	// --- LOCKS FOR MAPPINGS ---

	protected final ReadLock readLock;
	protected final WriteLock writeLock;

	// --- CONSTRUCTOR ---

	public ApiGateway() {

		// Init locks for static/dynamic mappings
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
			logger.info(nameOf(middleware, true) + " global middleware started.");
		}

		// Start routes and route-specific middlewares
		for (Route route : routes) {
			route.started(broker, globalMiddlewares);
			logRoute(route);
		}

		// Set last route (ServeStatic, "404 Not Found", etc.)
		lastRoute = new Route("", MappingPolicy.ALL, null, null, null);
		lastRoute.use(lastMiddleware);
		logRoute(lastRoute);
	}

	protected void logRoute(Route route) {
		StringBuilder msg = new StringBuilder(128);
		msg.append("Route installed on path \"");
		String path = route.getPath();
		if (path == null || path.isEmpty()) {
			msg.append('*');
		} else {
			msg.append(path);
		}
		msg.append("\" with");
		HttpMiddleware[] middlewares = route.getMiddlewares();
		if (middlewares == null || middlewares.length == 0) {
			msg.append("out middlewares.");
		} else if (middlewares.length == 1) {
			msg.append(' ');
			msg.append(nameOf(middlewares[0], true));
			msg.append(" middleware.");
		} else {
			msg.append(" the following middlewares: ");
			for (int i = 0; i < middlewares.length; i++) {
				msg.append(nameOf(middlewares[i], true));
				if (i < middlewares.length) {
					msg.append(", ");
				}
			}
		}
		logger.info(msg.toString());
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
					logger.info(nameOf(middleware, true) + " global middleware stopped.");
				}
			} catch (Exception ignored) {
				logger.warn("Unable to stop middleware!");
			}
		}

		// Stop routes (and route-specific middlewares)
		for (Route route : routes) {
			route.stopped(globalMiddlewares, debug);
		}
		setRoutes(new Route[0]);

		// Clear middleware registry
		globalMiddlewares.clear();

		// Log stop
		logger.info("ApiGateway server stopped.");
	}

	// --- SEND WEBSOCKET ---

	/**
	 * Send WebSocket via Moleculer Action.
	 */
	public Action sendWebSocket = (ctx) -> {
		if (ctx != null && ctx.params != null) {
			String endpoint = ctx.params.get("endpoint", "");
			Tree payload = ctx.params.get("payload");
			return sendWebSocket(endpoint, payload);
		}
		return false;
	};

	/**
	 * Send WebSocket directly via internal method call.
	 */
	public boolean sendWebSocket(String endpoint, Tree payload) {
		return webSocketRegistry.send(endpoint, payload.toString(false));
	}

	// --- PROCESS (NETTY OR J2EE) HTTP REQUEST ---

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

		// Add as List
		use(Arrays.asList(middlewares));
	}

	public void use(Collection<HttpMiddleware> middlewares) {

		// Has ApiGateway Started?
		checkStarted();

		// Add to global middlewares
		globalMiddlewares.addAll(middlewares);
	}

	protected void checkStarted() {
		if (broker != null) {
			throw new IllegalStateException("ApiGateway has already been started, no longer can be modified!");
		}
	}

	// --- SIMPLE ROUTE CREATION METHODS ---

	public ServeStatic addServeStatic(String path, String rootDirectory, HttpMiddleware... middlewares) {
		Route route = new Route(path, MappingPolicy.ALL, null, null, null);

		// Last middleware in this route
		route.use(new NotFound());

		// First is ServeStatic
		ServeStatic handler = new ServeStatic(path, rootDirectory);
		route.use(handler);
		if (middlewares != null && middlewares.length > 0) {
			route.use(middlewares);
		}

		// Create route
		addRoute(route);

		// Return ServeStatic handler (eg. to call "setReloadable" method)
		return handler;
	}

	// --- CREATING ROUTES ---

	/**
	 * Define a route for a list of Services. Eg. in the "service1" the
	 * service's "func" action will available on
	 * "http://host:port/service1/func").
	 *
	 * @param serviceList
	 *            list of services (eg. "service1,service2,service3")
	 * @param middlewares
	 *            optional middlewares (eg. CorsHeaders)
	 * 
	 * @return route the new route
	 */
	public Route addRoute(String serviceList, HttpMiddleware... middlewares) {
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
		Route route = new Route("", MappingPolicy.RESTRICT, null, whiteList, null);
		if (middlewares != null && middlewares.length > 0) {
			route.use(middlewares);
		}
		return addRoute(route);
	}

	/**
	 * Define a route to a "restful" action. The action will available on the
	 * specified path.
	 *
	 * @param path
	 *            path of the action (eg. "numbers/add/:a/:b" creates an
	 *            endpoint on "http://host:port/numbers/add/1/2")
	 * @param actionName
	 *            name of action (eg. "math.add")
	 * @param middlewares
	 *            optional middlewares (eg. CorsHeaders)
	 * 
	 * @return route the new route
	 */
	public Route addRoute(String path, String actionName, HttpMiddleware... middlewares) {
		return addRoute("GET", path, actionName, middlewares);
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
		Route route = new Route("", MappingPolicy.RESTRICT, null, null, new Alias[] { alias });
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
		return addRoute(new Route(path, mappingPolicy, opts, whitelist, aliases));
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

		// Has ApiGateway Started?
		checkStarted();

		// Set Template Engine
		if (templateEngine != null && route.getTemplateEngine() == null) {
			route.setTemplateEngine(templateEngine);
		}

		// Set "beforeCall" hook
		if (beforeCall != null && route.getBeforeCall() == null) {
			route.setBeforeCall(beforeCall);
		}

		// Set "afterCall" hook
		if (afterCall != null && route.getAfterCall() == null) {
			route.setAfterCall(afterCall);
		}

		// Add the new route to array of Routes
		Route[] copy = new Route[routes.length + 1];
		System.arraycopy(routes, 0, copy, 0, routes.length);
		copy[routes.length] = route;
		routes = copy;

		// Return route
		return route;
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public Route[] getRoutes() {

		Route[] copy = new Route[routes.length];
		System.arraycopy(routes, 0, copy, 0, copy.length);
		return copy;
	}

	public void setRoutes(Route[] routes) {

		// Has ApiGateway Started?
		checkStarted();

		Route[] newRoutes = Objects.requireNonNull(routes);
		this.routes = new Route[0];
		for (Route route : newRoutes) {
			addRoute(route);
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

		// Has ApiGateway Started?
		checkStarted();

		// Set last middleware
		this.lastMiddleware = lastMiddleware;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public AbstractTemplateEngine getTemplateEngine() {
		return templateEngine;
	}

	public void setTemplateEngine(AbstractTemplateEngine templateEngine) {

		// Has ApiGateway Started?
		checkStarted();

		// Set Template Engine
		this.templateEngine = templateEngine;

		// Set Template Engine on all Routes
		for (Route route : routes) {
			if (templateEngine == null || route.getTemplateEngine() == null) {
				route.setTemplateEngine(templateEngine);
			}
		}
	}

	public CallProcessor getBeforeCall() {
		return beforeCall;
	}

	public void setBeforeCall(CallProcessor beforeCall) {

		// Has ApiGateway Started?
		checkStarted();

		// Set method
		this.beforeCall = beforeCall;

		// Set "beforeCall" hook on all Routes
		for (Route route : routes) {
			if (beforeCall == null || route.getBeforeCall() == null) {
				route.setBeforeCall(beforeCall);
			}
		}
	}

	public CallProcessor getAfterCall() {
		return afterCall;
	}

	public void setAfterCall(CallProcessor afterCall) {

		// Has ApiGateway Started?
		checkStarted();

		// Set method
		this.afterCall = afterCall;

		// Set "afterCall" hook on all Routes
		for (Route route : routes) {
			if (afterCall == null || route.getAfterCall() == null) {
				route.setAfterCall(afterCall);
			}
		}
	}

}