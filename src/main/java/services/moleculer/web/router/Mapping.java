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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.datatree.Tree;
import io.datatree.dom.Cache;
import services.moleculer.ServiceBroker;
import services.moleculer.config.ServiceBrokerConfig;
import services.moleculer.context.CallOptions;
import services.moleculer.eventbus.Eventbus;
import services.moleculer.service.ServiceInvoker;
import services.moleculer.util.CheckedTree;
import services.moleculer.web.CallProcessor;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;
import services.moleculer.web.middleware.HttpMiddleware;
import services.moleculer.web.template.AbstractTemplateEngine;

public class Mapping implements RequestProcessor, HttpConstants {

	// --- PROPERTIES ---

	protected final String httpMethod;
	protected final String actionName;
	protected final Tree config;
	protected final Route route;
	protected final CallProcessor beforeCall;
	protected final CallProcessor afterCall;

	// --- LAST PROCESSOR ---

	protected RequestProcessor lastProcessor;

	// --- PATTERN ---

	protected final boolean isStatic;
	protected final String pathPrefix;
	protected final String pathPattern;
	protected final int hashCode;

	protected final Pattern pattern;

	// --- MATCHER CACHE ---

	protected final Cache<String, Matcher> cache;

	// --- INSTALLED MIDDLEWARES ---

	protected final Set<HttpMiddleware> installedMiddlewares = new HashSet<>(32);

	// --- CONSTRUCTOR ---

	public Mapping(ServiceBroker broker, String httpMethod, String pathPattern, String actionName,
			CallOptions.Options opts, AbstractTemplateEngine templateEngine, Route route, CallProcessor beforeCall,
			CallProcessor afterCall, ExecutorService executor) {

		this.httpMethod = "ALL".equals(httpMethod) ? null : httpMethod;
		this.pathPattern = pathPattern;
		this.actionName = Objects.requireNonNull(actionName);
		this.route = route;
		this.beforeCall = beforeCall;
		this.afterCall = afterCall;

		// Parse "path pattern"
		int starPos = pathPattern.indexOf('*');
		int colonPos = pathPattern.indexOf(':');
		isStatic = colonPos == -1 && starPos == -1;

		IndexedVariable[] variables = null;
		if (isStatic) {
			pathPrefix = pathPattern;
			pattern = null;
			cache = null;
		} else if (starPos > -1) {
			pathPrefix = pathPattern.substring(0, starPos);
			pattern = null;
			cache = null;
		} else {
			pathPrefix = pathPattern.substring(0, colonPos);

			// Parse regex
			if (!pathPattern.startsWith("/")) {
				pathPattern = '/' + pathPattern;
			}
			StringTokenizer st = new StringTokenizer(pathPattern, ":/.+?$^\\", true);

			boolean inName = false;
			String name = "";
			int index = 1;

			StringBuilder regex = new StringBuilder(pathPattern.length() * 2);
			regex.append('(');

			LinkedList<IndexedVariable> list = new LinkedList<>();
			while (st.hasMoreTokens()) {
				String token = st.nextToken();
				if ("+".equals(token) || "?".equals(token) || "$".equals(token) || "^".equals(token)
						|| "\\".equals(token)) {
					regex.append("\\").append(token);
					continue;
				}
				if (".".equals(token)) {
					if (inName) {
						regex.append(")(\\.");
						list.addLast(new IndexedVariable(index, name));
						index++;
						inName = false;
						name = "";
						continue;
					}
					regex.append("\\").append(token);
					continue;
				}
				if (":".equals(token)) {
					regex.append(")([^/]*");
					index++;
					inName = true;
					continue;
				}
				if ("/".equals(token)) {
					if (inName) {
						regex.append(")(").append(token);
						list.addLast(new IndexedVariable(index, name));
						index++;
						inName = false;
						name = "";
						continue;
					}
					regex.append(token);
					continue;
				}
				if (inName) {
					name += token;
					continue;
				}
				regex.append(token);
			}
			if (inName) {
				list.addLast(new IndexedVariable(index, name));
			}
			regex.append(')');
			pattern = Pattern.compile(regex.toString());
			variables = new IndexedVariable[list.size()];
			list.toArray(variables);

			// Create matcher cache
			cache = new Cache<>(64);
		}

		// Generate hashcode
		hashCode = pathPattern.hashCode();

		// Create config
		config = new Tree();
		config.put("action", actionName);
		config.put("pattern", pathPattern);
		config.put("static", isStatic);
		config.put("prefix", pathPrefix);
		if (opts != null) {
			config.put("nodeID", opts.nodeID);
			config.put("retryCount", opts.retryCount);
			config.put("timeout", opts.timeout);
		}

		// Set first RequestProcessor in the WebMiddleware chain
		ServiceBrokerConfig cfg = broker.getConfig();
		ServiceInvoker serviceInvoker = cfg.getServiceInvoker();
		ExecutorService runner = executor == null ? cfg.getExecutor() : executor;
		Eventbus eventbus = cfg.getEventbus();
		lastProcessor = new ActionInvoker(actionName, pattern, cache, variables, opts, serviceInvoker, templateEngine,
				route, beforeCall, afterCall, runner, eventbus);
	}

	// --- MATCH TEST ---

	public boolean matches(String httpMethod, String path) {
		if (this.httpMethod != null && !this.httpMethod.equals(httpMethod)) {
			return false;
		}
		if (isStatic) {
			return path.equals(pathPrefix);
		}
		if (!path.startsWith(pathPrefix)) {
			return false;
		}
		if (pattern != null) {
			Matcher matcher = cache.get(path);
			if (matcher == null) {
				matcher = pattern.matcher(path);
				if (matcher.matches()) {
					cache.put(path, matcher);				
					return true;
				}
				return false;
			}
			return true;
		}
		return true;
	}

	// --- ACTION WITH MIDDLEWARES ---

	public void use(Collection<HttpMiddleware> middlewares) {
		for (HttpMiddleware middleware : middlewares) {
			if (installedMiddlewares.add(middleware)) {
				RequestProcessor parent = lastProcessor.getParent();
				if (parent == null) {
					parent = lastProcessor;
				}
				Tree actionConfig = getActionConfig(parent);
				Tree processorConfig;
				if (actionConfig == null) {
					processorConfig = config;
				} else {
					processorConfig = config.clone();
					processorConfig.copyFrom(actionConfig);
				}
				RequestProcessor processor = middleware.install(lastProcessor, processorConfig);
				if (processor != null) {
					lastProcessor = processor;
				}
			}
		}
	}

	protected Tree getActionConfig(RequestProcessor parent) {
		if (parent == null || !(parent instanceof ActionInvoker)) {
			return null;
		}
		ActionInvoker invoker = (ActionInvoker) parent;
		if (invoker.actionName == null || invoker.actionName.isEmpty()) {
			return null;
		}
		int i = invoker.actionName.indexOf('.');
		if (i < 1) {
			return null;
		}
		Tree descriptor = route.getBroker().getConfig().getServiceRegistry().getDescriptor();
		Tree services = descriptor.get("services");
		if (services == null || services.isEmpty()) {
			return null;
		}
		String serviceName = invoker.actionName.substring(0, i);
		for (Tree service : services) {
			if (serviceName.equals(service.get("name", ""))) {
				Tree actions = service.get("actions");
				if (actions == null || actions.isEmpty()) {
					return null;
				}
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) actions.asObject();
				Object value = map.get(actionName);
				if (value == null) {
					return null;
				}
				return new CheckedTree(value);
			}
		}
		return null;
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
		lastProcessor.service(req, rsp);
	}

	// --- PARENT PROCESSOR ---

	@Override
	public RequestProcessor getParent() {
		return null;
	}

	// --- PROPERTY GETTERS ---

	public boolean isStatic() {
		return isStatic;
	}

	public String getPathPrefix() {
		return pathPrefix;
	}

	public String getHttpMethod() {
		return httpMethod;
	}

	public String getPathPattern() {
		return pathPattern;
	}

	// --- COLLECTION HELPERS ---

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (Mapping.class != obj.getClass()) {
			return false;
		}
		Mapping other = (Mapping) obj;
		return hashCode == other.hashCode && pathPattern.equals(other.pathPattern);
	}

}