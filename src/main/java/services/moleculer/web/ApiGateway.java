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
package services.moleculer.web;

import static services.moleculer.util.CommonUtils.nameOf;
import static services.moleculer.web.common.HttpConstants.CONTENT_LENGTH;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.eventbus.Listener;
import services.moleculer.eventbus.Subscribe;
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
	protected int cachedRoutes = 2048;

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

	/**
	 * Checked services with @HttpAlias annotations.
	 */
	protected Set<String> checkedNames = new HashSet<>();

	// --- TEMPLATE ENGINE ---

	/**
	 * HTML template engine.
	 */
	protected AbstractTemplateEngine templateEngine;

	// --- WEBSOCKET REGISTRY AND FILTER ---

	/**
	 * WebSocket registry (Netty or J2EE)
	 */
	protected WebSocketRegistry webSocketRegistry;

	/**
	 * WebSocket filter (access control)
	 */
	protected WebSocketFilter webSocketFilter;

	// --- CUSTOM PRE/POST PROCESSORS ---

	/**
	 * Custom message pre-processor.
	 */
	protected CallProcessor beforeCall;

	/**
	 * Custom message post-processor.
	 */
	protected CallProcessor afterCall;

	// --- CUSTOM EXECUTOR SERVICE ---

	/**
	 * Custom Action Executor (null = use the shared ExecutorService of the
	 * MessageBroker).
	 */
	protected ExecutorService executor;

	// --- LOCKS FOR MAPPINGS ---

	protected final ReadLock readLock;
	protected final WriteLock writeLock;

	// --- SEND WEBSOCKET ---

	/**
	 * Send WebSocket via broadcasted Moleculer Event.
	 */
	@Subscribe("websocket.send")
	public Listener webSocketListener = ctx -> {
		if (webSocketRegistry == null) {
			return;
		}
		if (ctx.params == null || ctx.params.isEmpty()) {
			logger.warn("Empty websocket packet, all parameters are missing!");
			return;
		}
		String path = ctx.params.get("path", "");
		if (path == null || path.isEmpty()) {
			logger.warn("Invalid websocket packet, the \"path\" parameter is required: " + ctx.params);
			return;
		}
		if (path.charAt(0) != '/') {
			path = '/' + path;
		}
		Tree data = ctx.params.get("data");
		String msg;
		if (data == null) {
			msg = "null";
		} else {
			msg = data.toString(null, false, false);
		}
		webSocketRegistry.send(path, msg);
	};

	// --- AUTODEPLOYER ---

	@Subscribe("$services.changed")
	private Listener autoDeployListener = ctx -> {

		// Local service?
		if (ctx.params == null || !ctx.params.get("localService", false)) {
			return;
		}

		// Check annotations
		Tree descriptor = broker.getConfig().getServiceRegistry().getDescriptor();
		Tree services = descriptor.get("services");
		if (services == null || services.isEmpty()) {
			return;
		}
		StringBuilder msg = new StringBuilder(128);
		for (Tree service : services) {
			String serviceName = service.get("name", "");
			if (serviceName == null || serviceName.isEmpty() || !checkedNames.add(serviceName)) {
				continue;
			}
			checkedNames.add(serviceName);
			Tree actions = service.get("actions");
			if (actions == null) {
				continue;
			}
			for (Tree action : actions) {
				Tree httpAlias = action.get("httpAlias");
				if (httpAlias == null) {
					continue;
				}
				String actionName = action.get("name", "");
				String httpMethod = httpAlias.get("method", "ALL");
				String pathPattern = httpAlias.get("path", "");
				if (pathPattern == null || pathPattern.isEmpty()) {

					// Deploy as "/service/action"
					pathPattern = '/' + actionName.replace('.', '/');
				}
				String routePath = httpAlias.get("route", "");
				if (routePath == null) {
					routePath = "";
				}
				Route route = null;
				for (Route test : routes) {
					if (test.getPath().equals(routePath)) {
						route = test;
						break;
					}
				}
				if (route == null) {
					route = addRoute(new Route(routePath));
				}
				Alias alias = new Alias(httpMethod, pathPattern, actionName);
				route.addAlias(alias);
				logAlias(msg, route, alias);
			}
		}
	};

	// --- CONSTRUCTORS ---

	public ApiGateway() {
		this((String[]) null);
	}

	public ApiGateway(String... whiteListEntries) {

		// Init locks for static/dynamic mappings
		ReentrantReadWriteLock lock = new ReentrantReadWriteLock(false);
		readLock = lock.readLock();
		writeLock = lock.writeLock();

		// Add basic route for REST services ("service1*", "service2.action")
		if (whiteListEntries != null && whiteListEntries.length > 0) {
			addRoute(new Route()).addToWhiteList(whiteListEntries);
		}
	}

	// --- START GATEWAY INSTANCE ---

	/**
	 * Initializes gateway instance.
	 *
	 * @param broker
	 *            parent ServiceBroker
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
			logger.info(nameOf(middleware, false) + " global middleware started.");
		}

		// Start routes and route-specific middlewares
		for (Route route : routes) {
			route.started(broker, globalMiddlewares);
			logRoute(route);
		}

		// Set last route (ServeStatic, "404 Not Found", etc.)
		lastRoute = new Route("", lastMiddleware);
		lastRoute.setMappingPolicy(MappingPolicy.ALL);
		lastRoute.started(broker, globalMiddlewares);
		logRoute(lastRoute);

		// Prepare mappings
		LinkedList<Mapping> mappingList = new LinkedList<>();
		for (Route route : routes) {
			Alias[] aliases = route.getAliases();
			if (aliases != null) {
				for (Alias alias : aliases) {
					String httpMethod = alias.getHttpMethod();
					if (httpMethod == null || Alias.ALL.equals(httpMethod) || Alias.REST.equals(httpMethod)) {
						continue;
					}
					String path = alias.getPathPattern();
					Mapping mapping = route.findMapping(httpMethod, path);
					if (mapping != null) {
						mappingList.addLast(mapping);
					}
				}
			}
		}
		if (!mappingList.isEmpty()) {
			Mapping[] mappingArray = new Mapping[mappingList.size()];
			mappingList.toArray(mappingArray);
			Arrays.sort(mappingArray, (m1, m2) -> {
				int v1 = m1.getVariables();
				int v2 = m2.getVariables();
				if (v1 == v2) {
					v1 = m1.getPathPrefix().length();
					v2 = m2.getPathPrefix().length();
				}
				return Integer.compare(v1, v2);
			});
			cachedRoutes = Math.max(cachedRoutes, mappingArray.length);
			writeLock.lock();
			try {
				for (Mapping mapping : mappingArray) {
					if (!globalMiddlewares.isEmpty()) {
						mapping.use(globalMiddlewares);
					}
					if (mapping.isStatic()) {
						String staticKey = mapping.getHttpMethod() + ' ' + mapping.getPathPrefix();
						staticMappings.put(staticKey, mapping);
						if (debug) {
							logger.info("New mapping for \"" + mapping.getPathPrefix()
									+ "\" stored in the static mapping cache (key: " + staticKey + ").");
						}
					} else {
						dynamicMappings.addLast(mapping);
						if (debug) {
							logger.info("New mapping for \"" + mapping.getPathPrefix()
									+ "\" stored in the dynamic mapping cache.");
						}
					}
				}
			} finally {
				writeLock.unlock();
			}
		}
	}

	protected void logRoute(Route route) {
		if (!debug) {
			return;
		}
		StringBuilder msg = new StringBuilder(128);
		msg.append("Route installed on path \"");
		String path = route.getPath();
		if (path == null || path.isEmpty()) {
			msg.append('/');
		} else {
			msg.append(path);
		}
		msg.append("\" with");
		HttpMiddleware[] middlewares = route.getMiddlewares();
		if (middlewares == null || middlewares.length == 0) {
			msg.append("out middlewares.");
		} else if (middlewares.length == 1) {
			msg.append(' ');
			msg.append(nameOf(middlewares[0], false));
			msg.append(" middleware.");
		} else {
			msg.append(" the following middlewares: ");
			for (int i = 0; i < middlewares.length; i++) {
				msg.append(nameOf(middlewares[i], false));
				if (i < middlewares.length - 1) {
					msg.append(", ");
				}
			}
			msg.append('.');
		}
		logger.info(msg.toString());

		Alias[] aliases = route.getAliases();
		if (aliases != null && aliases.length > 0) {
			for (Alias alias : aliases) {
				logAlias(msg, route, alias);
			}
		}

		String[] whiteList = route.getWhiteList();
		if (whiteList != null && whiteList.length > 0) {
			for (String whiteListEntry : whiteList) {
				msg.setLength(0);
				msg.append("Path \"");
				String p = route.getPath();
				if (p != null && !p.isEmpty() && !"/".equals(p)) {
					msg.append(p);
				}
				msg.append(whiteListEntry);
				msg.append("\" added to whitelist.");
				logger.info(msg.toString());
			}
		}
	}

	protected void logAlias(StringBuilder msg, Route route, Alias alias) {
		if (!debug) {
			return;
		}
		msg.setLength(0);
		msg.append(alias.getHttpMethod());
		msg.append(" methods with path \"");
		String p = route.getPath();
		if (p != null && !p.isEmpty() && !"/".equals(p)) {
			msg.append(p);
		}
		msg.append(alias.getPathPattern());
		msg.append("\" mapped to \"");
		msg.append(alias.getActionName());
		msg.append("\" action.");
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
					logger.info(nameOf(middleware, false) + " global middleware stopped.");
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
		lastRoute.stopped(globalMiddlewares, debug);

		// Clear middleware registry and mappings
		globalMiddlewares.clear();
		clearMappings();

		// Log stop
		logger.info("ApiGateway server stopped.");
	}

	protected void clearMappings() {
		writeLock.lock();
		try {
			staticMappings.clear();
			dynamicMappings.clear();
		} finally {
			writeLock.unlock();
		}
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
		String staticKey = httpMethod + ' ' + path;
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
						logger.info("New mapping for \"" + mapping.getPathPrefix()
								+ "\" stored in the static mapping cache (key: " + staticKey + ").");
					}
				} else {
					dynamicMappings.addLast(mapping);
					if (dynamicMappings.size() > cachedRoutes) {
						dynamicMappings.removeFirst();
					}
					if (debug) {
						logger.info("New mapping for \"" + mapping.getPathPrefix()
								+ "\" stored in the dynamic mapping cache.");
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

		// Add to global middlewares
		HashSet<HttpMiddleware> newMiddlewares = new HashSet<>();
		for (HttpMiddleware middleware : middlewares) {
			if (globalMiddlewares.add(middleware)) {
				newMiddlewares.add(middleware);
			}
		}

		// Already started?
		if (broker != null && !newMiddlewares.isEmpty()) {
			for (Route route : routes) {
				route.use(newMiddlewares);
			}
			lastRoute.use(newMiddlewares);
			for (HttpMiddleware middleware : newMiddlewares) {
				try {
					middleware.started(broker);
				} catch (Exception cause) {
					logger.warn("Unable to start middleware!", cause);
				}
			}
			clearMappings();
		}
	}

	// --- CREATING ROUTES ---

	public Route addRoute() {
		return addRoute(new Route());
	}

	/**
	 * Define a route for a list of Service (eg. in the "service" the service's
	 * "action" action will available on
	 * "http://host:port/path/service/action").
	 *
	 * @param path
	 *            root path to services (can be null or empty)
	 * @param serviceList
	 *            list of services and/or actions (eg.
	 *            "service1, service2.action, service3, service4.action")
	 * @param middlewares
	 *            optional middlewares (eg. CorsHeaders)
	 * 
	 * @return route the new route
	 */
	public Route addRoute(String path, String serviceList, HttpMiddleware... middlewares) {

		// Create whitelist entry for all services
		String[] serviceNames = serviceList.split(",");
		LinkedList<String> list = new LinkedList<>();
		for (String serviceName : serviceNames) {
			serviceName = serviceName.trim();
			if (serviceName.isEmpty()) {
				continue;
			}
			if (serviceName.indexOf('.') == -1) {
				serviceName = serviceName + "*";
			}
			if (!serviceName.startsWith("/")) {
				serviceName = "/" + serviceName;
			}
			list.addLast(serviceName);
		}
		String[] whiteList = new String[list.size()];
		list.toArray(whiteList);

		// Create route
		Route route = new Route(path);
		route.setMappingPolicy(MappingPolicy.RESTRICT);
		route.setWhiteList(whiteList);

		// Add custom middlewares
		route.use(middlewares);

		// Register route
		return addRoute(route);
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

		// Set Executor
		if (executor != null && route.getExecutor() == null) {
			route.setExecutor(executor);
		}

		// Add the new route to array of Routes
		Route[] copy = new Route[routes.length + 1];
		System.arraycopy(routes, 0, copy, 0, routes.length);
		copy[routes.length] = route;
		routes = copy;

		// Already started? -> start
		if (broker != null) {
			try {
				route.started(broker, globalMiddlewares);
			} catch (Exception cause) {
				logger.warn("Unable to start route!", cause);
			}
		}

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
		if (this.lastMiddleware != lastMiddleware) {
			this.lastMiddleware = lastMiddleware;

			// Already started?
			if (broker != null) {
				lastRoute = new Route("", lastMiddleware);
				try {
					lastRoute.started(broker, globalMiddlewares);
				} catch (Exception cause) {
					logger.warn("Unable to start last route!", cause);
				}
			}
		}
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

		// Set Template Engine
		this.templateEngine = templateEngine;

		// Set Template Engine of Routes
		if (templateEngine != null) {
			for (Route route : routes) {
				if (route.getTemplateEngine() == null) {
					route.setTemplateEngine(templateEngine);
				}
			}
		}
	}

	public CallProcessor getBeforeCall() {
		return beforeCall;
	}

	public void setBeforeCall(CallProcessor beforeCall) {

		// Set method
		this.beforeCall = beforeCall;

		// Set "beforeCall" processor of Routes
		if (beforeCall != null) {
			for (Route route : routes) {
				if (route.getBeforeCall() == null) {
					route.setBeforeCall(beforeCall);
				}
			}
		}
	}

	public CallProcessor getAfterCall() {
		return afterCall;
	}

	public void setAfterCall(CallProcessor afterCall) {

		// Set method
		this.afterCall = afterCall;

		// Set "afterCall" processor of Routes
		if (afterCall != null) {
			for (Route route : routes) {
				if (route.getAfterCall() == null) {
					route.setAfterCall(afterCall);
				}
			}
		}
	}

	public WebSocketRegistry getWebSocketRegistry() {
		return webSocketRegistry;
	}

	public void setWebSocketRegistry(WebSocketRegistry webSocketRegistry) {
		this.webSocketRegistry = Objects.requireNonNull(webSocketRegistry);
		if (webSocketFilter != null) {
			this.webSocketRegistry.setWebSocketFilter(webSocketFilter);
		}
	}

	public WebSocketFilter getWebSocketFilter() {
		return webSocketFilter;
	}

	public void setWebSocketFilter(WebSocketFilter webSocketFilter) {
		this.webSocketFilter = webSocketFilter;
		if (webSocketRegistry != null) {
			webSocketRegistry.setWebSocketFilter(webSocketFilter);
		}
	}

	public ExecutorService getExecutor() {
		return executor;
	}

	public void setExecutor(ExecutorService executor) {
		this.executor = executor;

		// Set Executor of Routes
		if (executor != null) {
			for (Route route : routes) {
				if (route.getExecutor() == null) {
					route.setExecutor(executor);
				}
			}
		}
	}

	// --- PARENT PROCESSOR ---

	@Override
	public RequestProcessor getParent() {
		return null;
	}

}