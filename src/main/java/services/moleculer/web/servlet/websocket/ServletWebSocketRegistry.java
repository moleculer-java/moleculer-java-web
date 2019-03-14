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

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereRequestImpl;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResponseImpl;
import org.atmosphere.util.ExecutorsFactory;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketProcessor.WebSocketException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import services.moleculer.ServiceBroker;
import services.moleculer.config.ServiceBrokerConfig;
import services.moleculer.stream.PacketStream;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebSocketRegistry;
import services.moleculer.web.common.Endpoint;

public class ServletWebSocketRegistry extends WebSocketRegistry implements WebSocketHandler {

	// --- LOGGER ---

	private static final Logger logger = LoggerFactory.getLogger(ServletWebSocketRegistry.class);

	// --- VARIABLES ---

	protected AtmosphereFramework atmosphere = new AtmosphereFramework(false, false);

	// --- CONSTRUCTOR ---

	public ServletWebSocketRegistry(ServletConfig config, ServiceBroker broker, long cleanupSeconds, boolean async)
			throws ServletException {
		super(broker, cleanupSeconds);

		// Set default properties (based on performance measurements)
		final HashMap<String, String> map = new HashMap<>();
		map.put("org.atmosphere.websocket.suppressJSR356", "true");
		map.put("org.atmosphere.cpr.AtmosphereInterceptor.disableDefaults", "true");
		map.put("org.atmosphere.cpr.Broadcaster.supportOutOfOrderBroadcast", "false");
		map.put("org.atmosphere.cpr.Broadcaster.threadWaitTime", "0");
		map.put("org.atmosphere.cpr.broadcasterCacheClass",
				"services.moleculer.web.servlet.websocket.NullBroadcasterCache");

		// Set async mode
		if (async) {
			atmosphere.setUseBlockingImplementation(false);
			atmosphere.setUseServlet30(true);
		} else {
			atmosphere.setUseBlockingImplementation(true);
			atmosphere.setUseServlet30(false);
		}

		// Init Atmosphere
		ServletConfig wrapper = new ServletConfig() {

			@Override
			public String getServletName() {
				return config.getServletName();
			}

			@Override
			public ServletContext getServletContext() {
				return config.getServletContext();
			}

			@Override
			public Enumeration<String> getInitParameterNames() {
				Vector<String> names = new Vector<>(map.keySet());
				Enumeration<String> e = config.getInitParameterNames();
				while (e.hasMoreElements()) {
					String name = e.nextElement();
					if (!names.contains(name)) {
						names.add(name);
					}
				}
				return names.elements();
			}

			@Override
			public String getInitParameter(String name) {
				String value = config.getInitParameter(name);
				if (value != null) {
					return value;
				}
				return map.get(name);
			}

		};
		atmosphere.init(wrapper);
		atmosphere.addWebSocketHandler(this);

		// Use common executor and scheduler
		ServiceBrokerConfig cfg = broker.getConfig();
		atmosphere.getAtmosphereConfig().properties().put(ExecutorsFactory.BROADCASTER_THREAD_POOL, cfg.getExecutor());
		atmosphere.getAtmosphereConfig().properties().put(ExecutorsFactory.SCHEDULER_THREAD_POOL, cfg.getScheduler());
	}

	public void stopped() {
		atmosphere.destroy();
		super.stopped();
	}

	public void service(HttpServletRequest req, HttpServletResponse rsp) throws IOException, ServletException {
		atmosphere.doCometSupport(AtmosphereRequestImpl.wrap(req), AtmosphereResponseImpl.wrap(rsp));
	}

	// --- WEBSOCKET HANDLERS ---

	@Override
	public void onOpen(WebSocket webSocket) throws IOException {
		AtmosphereResource resource = webSocket.resource();
		if (resource == null) {
			return;
		}
		AtmosphereRequest atmosphereRequest = resource.getRequest();
		if (atmosphereRequest == null) {
			return;
		}
		String path = atmosphereRequest.getRequestURI();
		if (webSocketFilter != null) {
			WebRequest webRequest = new WebRequest() {

				@Override
				public final boolean isMultipart() {
					return false;
				}

				@Override
				public final String getQuery() {
					return atmosphereRequest.getQueryString();
				}

				@Override
				public final String getPath() {
					return path;
				}

				@Override
				public final String getMethod() {
					return atmosphereRequest.getMethod();
				}

				@Override
				public final Iterator<String> getHeaders() {
					Enumeration<String> headers = atmosphereRequest.getHeaderNames();
					return new Iterator<String>() {

						@Override
						public final boolean hasNext() {
							return headers.hasMoreElements();
						}

						@Override
						public final String next() {
							return headers.nextElement();
						}

					};
				}

				@Override
				public final String getHeader(String name) {
					return atmosphereRequest.getHeader(name);
				}

				@Override
				public final String getContentType() {
					return atmosphereRequest.getContentType();
				}

				@Override
				public final int getContentLength() {
					return atmosphereRequest.getContentLength();
				}

				@Override
				public final PacketStream getBody() {
					return null;
				}

				@Override
				public final String getAddress() {
					return atmosphereRequest.getRemoteAddr();
				}

			};
			boolean accept = webSocketFilter.onConnect(webRequest);
			if (!accept) {
				resource.close();
				logger.info(
						"Inbound WebSocket connection closed due to rejection of the WebSocket Filter: " + webSocket);
				return;
			}
		}
		register(path, toEnpoint(webSocket));
	}

	@Override
	public void onByteMessage(WebSocket webSocket, byte[] data, int offset, int length) throws IOException {

		// Not implemented (use REST)
	}

	/**
	 * Ping-pong simulation with a special message ("!").
	 */
	@Override
	public void onTextMessage(WebSocket webSocket, String data) throws IOException {
		if (data != null && "!".endsWith(data)) {
			webSocket.write("!");
		}
	}

	@Override
	public void onClose(WebSocket webSocket) {
		onError(webSocket, null);
	}

	@Override
	public void onError(WebSocket webSocket, WebSocketException t) {
		AtmosphereResource resource = webSocket.resource();
		if (resource == null) {
			return;
		}
		AtmosphereRequest atmosphereRequest = resource.getRequest();
		if (atmosphereRequest == null) {
			return;
		}
		deregister(atmosphereRequest.getRequestURI(), toEnpoint(webSocket));
	}

	protected Endpoint toEnpoint(WebSocket webSocket) {
		return new Endpoint() {

			@Override
			public final void send(String message) {
				try {
					webSocket.write(message);					
				} catch (Exception ignored) {
					webSocket.close();
				}
			}

			@Override
			public final boolean isOpen() {
				return webSocket.isOpen();
			}

			@Override
			public int hashCode() {
				return webSocket.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == null || !(obj instanceof Endpoint)) {
					return false;
				}
				Endpoint e = (Endpoint) obj;
				return e.getInternal() == webSocket;
			}

			@Override
			public Object getInternal() {
				return webSocket;
			}
			
		};
	}
	
}