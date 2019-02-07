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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.context.CallOptions;
import services.moleculer.service.ServiceInvoker;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;
import services.moleculer.web.middleware.HttpMiddleware;

public class Mapping implements RequestProcessor, HttpConstants {

	// --- PROPERTIES ---

	protected final String httpMethod;
	protected final String actionName;
	protected final boolean isStatic;
	protected final String pathPrefix;
	protected final int hashCode;
	protected final Tree config;

	// --- LAST PROCESSOR ---

	protected RequestProcessor lastProcessor;

	// --- INSTALLED MIDDLEWARES ---

	protected HashSet<HttpMiddleware> checkedMiddlewares = new HashSet<>(32);

	// --- CONSTRUCTOR ---

	public Mapping(ServiceBroker broker, String httpMethod, String pathPattern, String actionName,
			CallOptions.Options opts) {
		this.httpMethod = "ALL".equals(httpMethod) ? null : httpMethod;
		this.actionName = Objects.requireNonNull(actionName);

		// Parse "path pattern"
		int starPos = pathPattern.indexOf('*');
		isStatic = pathPattern.indexOf(':') == -1 && starPos == -1;
		String[] tokens = null;
		ArrayList<Integer> indexList = new ArrayList<>();
		ArrayList<String> nameList = new ArrayList<>();
		if (isStatic) {
			pathPrefix = pathPattern;
		} else if (starPos > -1) {
			pathPrefix = pathPattern.substring(0, starPos);
		} else {
			tokens = pathPattern.split("/");
			int endIndex = 0;
			for (int i = 0; i < tokens.length; i++) {
				String token = tokens[i].trim();
				if (token.startsWith(":")) {
					token = token.substring(1);
					indexList.add(i);
					nameList.add(token);
					continue;
				}
				if (indexList.isEmpty()) {
					endIndex += token.length() + 1;
				}
			}
			pathPrefix = pathPattern.substring(0, endIndex);
		}
		int[] indexes = new int[indexList.size()];
		String[] names = new String[nameList.size()];
		for (int i = 0; i < indexes.length; i++) {
			indexes[i] = indexList.get(i);
			names[i] = nameList.get(i);
		}

		// Generate hashcode
		final int prime = 31;
		int result = 1;
		result = prime * result + actionName.hashCode();
		result = prime * result + pathPrefix.hashCode();
		hashCode = result;

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
		ServiceInvoker serviceInvoker = broker.getConfig().getServiceInvoker();
		lastProcessor = new ActionInvoker(actionName, pathPattern, isStatic, pathPrefix, indexes, names, opts, serviceInvoker);
	}

	// --- MATCH TEST ---

	public boolean matches(String httpMethod, String path) {
		if (this.httpMethod != null && !this.httpMethod.equals(httpMethod)) {
			return false;
		}
		if (isStatic) {
			if (!path.equals(pathPrefix)) {
				return false;
			}
		} else {
			if (!path.startsWith(pathPrefix)) {
				return false;
			}
		}
		return true;
	}

	// --- ACTION WITH MIDDLEWARES ---

	public void use(Collection<HttpMiddleware> middlewares) {
		for (HttpMiddleware middleware : middlewares) {
			if (checkedMiddlewares.add(middleware)) {
				RequestProcessor processor = middleware.install(lastProcessor, config);
				if (processor != null) {
					lastProcessor = processor;
				}
			}
		}
	}
	
	// --- PROCESS (SERVLET OR NETTY) HTTP REQUEST ---

	@Override
	public void service(WebRequest req, WebResponse rsp) throws Exception {
		lastProcessor.service(req, rsp);
	}

	// --- PROPERTY GETTERS ---

	public boolean isStatic() {
		return isStatic;
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
		return hashCode == other.hashCode && actionName.equals(other.actionName) && pathPrefix.equals(other.pathPrefix);
	}

}