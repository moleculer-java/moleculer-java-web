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
package services.moleculer.web.servlet.websocket;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import services.moleculer.ServiceBroker;
import services.moleculer.stream.PacketStream;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebSocketRegistry;
import services.moleculer.web.common.HttpConstants;

public class ServletWebSocketRegistry extends WebSocketRegistry {

	// --- VARIABLES ---

	protected final String contextPath;
	protected final int contextPathLength;

	// --- CONSTRUCTOR ---

	public ServletWebSocketRegistry(ServletConfig config, ServiceBroker broker, long cleanupSeconds)
			throws ServletException {
		super(broker, cleanupSeconds);

		// Get context path
		contextPath = config.getServletContext().getContextPath();
		contextPathLength = contextPath.length();

		// Link WebSocket endpoints to this registry
		ServletContext ctx = config.getServletContext();
		EndpointConfigurator configurator = (EndpointConfigurator) ctx.getAttribute("moleculer.endpoint.configurator");
		if (configurator != null) {
			configurator.setServletWebSocketRegistry(this);
		}
		ctx.setAttribute("moleculer.servlet.registry", this);
	}

	// --- WEBSOCKET HANDLERS ---

	protected void onOpen(Session session) {

		// Get "pathInfo"
		String pathInfo = getPathInfo(session);

		// Check access
		if (webSocketFilter != null) {
			boolean accept = webSocketFilter.onConnect(new WebRequest() {

				@Override
				public final boolean isMultipart() {
					return false;
				}

				@Override
				public final String getQuery() {
					return session.getQueryString();
				}

				@Override
				public final String getPath() {
					return pathInfo;
				}

				@Override
				public final String getMethod() {
					return "GET";
				}

				@Override
				public final Iterator<String> getHeaders() {
					return getHeaderMap().keySet().iterator();
				}

				@Override
				public final String getHeader(String name) {
					return getHeader(name);
				}

				@Override
				public final String getContentType() {
					return getHeader(HttpConstants.CONTENT_TYPE, "text/plain");
				}

				@Override
				public final int getContentLength() {
					try {
						return Integer.parseInt(getHeader(HttpConstants.CONTENT_LENGTH, "-1"));
					} catch (Exception ignored) {
					}
					return -1;
				}

				@Override
				public final PacketStream getBody() {
					return new PacketStream(null, null);
				}

				@Override
				public final String getAddress() {
					return "0.0.0.0";
				}

				@Override
				public final Object getInternalObject() {
					return session;
				}
				
				@SuppressWarnings("unchecked")
				private final Map<String, List<String>> getHeaderMap() {
					Map<String, Object> props = session.getUserProperties();
					if (props == null) {
						return Collections.emptyMap();
					}
					return (Map<String, List<String>>) props.get("moleculer.headers");
				}

				private final String getHeader(String name, String defaultValue) {
					Map<String, List<String>> map = getHeaderMap();
					if (map.isEmpty()) {
						return defaultValue;
					}
					for (String key : map.keySet()) {
						if (name.equalsIgnoreCase(key)) {
							List<String> list = map.get(key);
							if (list != null && !list.isEmpty()) {
								return list.get(0);
							}
						}
					}
					return defaultValue;
				}

			});
			if (!accept) {
				try {
					session.close();
				} catch (Exception ignored) {
				}
				return;
			}
		}

		// Register
		ServletEndpoint endpoint = new ServletEndpoint(session, true);
		register(pathInfo, endpoint);

		// Add heartbeat handler
		session.addMessageHandler(new MessageHandler.Whole<String>() {

			public final void onMessage(String text) {
				endpoint.send("!");
			}

		});
	}

	protected void onClose(Session session) {
		deregister(getPathInfo(session), new ServletEndpoint(session, false));
	}

	protected String getPathInfo(Session session) {
		String path = session.getRequestURI().getPath();
		if (contextPathLength > 1 && path.startsWith(contextPath)) {
			return path.substring(contextPathLength);
		}
		return path;
	}

}