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
package services.moleculer.web.servlet;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import javax.servlet.AsyncContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import services.moleculer.web.servlet.request.NonBlockingWebRequest;
import services.moleculer.web.servlet.response.NonBlockingWebResponse;

@WebServlet(asyncSupported = true)
public class NonBlockingMoleculerServlet extends AbstractMoleculerServlet {

	// --- UID ---

	private static final long serialVersionUID = 3491086564959030123L;

	// --- COMMON TASK EXECUTOR ---

	protected ExecutorService executor;

	// --- INIT / START ---

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		// Get executor
		executor = broker.getConfig().getExecutor();
	}

	// --- NON-BLOCKING SERVICE ---

	@Override
	public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
		final HttpServletResponse rsp = (HttpServletResponse) response;
		try {

			// Start async
			AsyncContext async = request.startAsync(request, response);

			// Process request
			executor.execute(() -> {
				try {
					gateway.service(new NonBlockingWebRequest(broker, async, (HttpServletRequest) request),
							new NonBlockingWebResponse(async, rsp));
				} catch (Throwable cause) {
					handleError(rsp, cause, async);
				}
			});

		} catch (Throwable cause) {
			
			// Fatal error
			handleError(rsp, cause, null);
		}
	}

	// --- CLOSE REQUEST WITH ERROR ---

	protected void handleError(HttpServletResponse rsp, Throwable cause, AsyncContext async) {
		try {
			if (gateway == null) {
				rsp.sendError(404);
			} else {
				rsp.sendError(500);
				getServletContext().log("Unable to process request!", cause);
			}
		} catch (Throwable ignored) {
		} finally {
			if (async != null) {
				async.complete();
			}
		}
	}

}