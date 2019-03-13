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
package services.moleculer.web.servlet;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import services.moleculer.web.servlet.request.BlockingWebRequest;
import services.moleculer.web.servlet.response.BlockingWebResponse;
import services.moleculer.web.servlet.websocket.ServletWebSocketRegistry;

/**
 * Old type (blocking) Servlet. Can be used for REST services and file
 * servicing. It can be used in the same way with every Middleware as with
 * non-blocking Moleculer servlet.
 */
public class BlockingMoleculerServlet extends AbstractMoleculerServlet {

	// --- UID ---

	private static final long serialVersionUID = 1669628991868900133L;

	// --- CONSTANTS ---

	protected long timeout;

	// --- INIT / START ---

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		// Get timeout
		String value = config.getInitParameter("response.timeout");
		if (value == null || value.isEmpty()) {
			timeout = 1000L * 60 * 3;
		} else {
			timeout = Long.parseLong(value);
		}

		// Create WebSocket registry
		webSocketRegistry = new ServletWebSocketRegistry(config, executor, scheduler, false);
		gateway.setWebSocketRegistry(webSocketRegistry);
	}

	// --- BLOCKING SERVICE ---

	@Override
	public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
		final HttpServletRequest req = (HttpServletRequest) request;
		final HttpServletResponse rsp = (HttpServletResponse) response;
		try {

			// WebSocket handling
			if (req.getHeader("Upgrade") != null) {
				webSocketRegistry.service(req, rsp);
				return;
			}

			// Process request
			BlockingWebResponse bwr = new BlockingWebResponse(rsp);
			gateway.service(new BlockingWebRequest(broker, req), bwr);
			bwr.waitFor(timeout);

		} catch (TimeoutException timeout) {
			try {
				rsp.sendError(408);
				getServletContext().log("Unexpected timeout exception occured!", timeout);
			} catch (Throwable ignored) {
			}
		} catch (Throwable cause) {
			try {
				if (gateway == null) {
					rsp.sendError(404);
				} else {
					rsp.sendError(500);
					getServletContext().log("Unable to process request!", cause);
				}
			} catch (Throwable ignored) {
			}
		}
	}

}