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
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequestImpl;
import org.atmosphere.cpr.AtmosphereResponseImpl;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.util.ExecutorsFactory;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketHandler;
import org.atmosphere.websocket.WebSocketProcessor.WebSocketException;

import services.moleculer.web.WebSocketRegistry;

public class ServletWebSocketRegistry implements WebSocketRegistry, WebSocketHandler {

	protected AtmosphereFramework atmosphere = new AtmosphereFramework(false, false);

	public ServletWebSocketRegistry(ServletConfig config, ExecutorService executor, ScheduledExecutorService scheduler,
			boolean async) throws ServletException {

		// Set default properties (based on performance measurements)
		final HashMap<String, String> map = new HashMap<>();
		map.put("org.atmosphere.websocket.suppressJSR356", "true");
		map.put("org.atmosphere.cpr.AtmosphereInterceptor.disableDefaults", "true");
		map.put("org.atmosphere.cpr.broadcasterCacheClass", "services.moleculer.web.servlet.NullBroadcasterCache");
		map.put("org.atmosphere.cpr.Broadcaster.supportOutOfOrderBroadcast", "false");
		map.put("org.atmosphere.cpr.broadcasterClass", "services.moleculer.web.servlet.SimpleBroadcaster");
		map.put("org.atmosphere.cpr.Broadcaster.threadWaitTime", "0");

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
		atmosphere.getAtmosphereConfig().properties().put(ExecutorsFactory.BROADCASTER_THREAD_POOL, executor);
		atmosphere.getAtmosphereConfig().properties().put(ExecutorsFactory.SCHEDULER_THREAD_POOL, scheduler);
	}

	public void stopped() {
		atmosphere.destroy();
	}

	@Override
	public void send(String path, String message) {
		Broadcaster broadcaster = atmosphere.getBroadcasterFactory().lookup(path);
		if (broadcaster != null) {
			broadcaster.broadcast(message);
		}
	}

	public void service(HttpServletRequest req, HttpServletResponse rsp) throws IOException, ServletException {
		atmosphere.doCometSupport(AtmosphereRequestImpl.wrap(req), AtmosphereResponseImpl.wrap(rsp));
	}

	// --- WEBSOCKET HANDLERS ---

	@Override
	public void onOpen(WebSocket webSocket) throws IOException {

		// TODO Check Path
		System.out.println("OPEN " + webSocket);
	}

	@Override
	public void onByteMessage(WebSocket webSocket, byte[] data, int offset, int length) throws IOException {
	}

	@Override
	public void onTextMessage(WebSocket webSocket, String data) throws IOException {
	}

	@Override
	public void onClose(WebSocket webSocket) {
	}

	@Override
	public void onError(WebSocket webSocket, WebSocketException t) {
	}

}