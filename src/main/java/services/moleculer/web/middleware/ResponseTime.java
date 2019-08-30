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
package services.moleculer.web.middleware;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.datatree.Tree;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;

/**
 * Adds a header "X-Response-Time" to the response, containing the time taken in
 * MILLISECONDS to process the request.
 */
@Name("Response Time")
public class ResponseTime extends HttpMiddleware {

	// --- PROPERTIES ---

	protected String headerName = "X-Response-Time";

	// --- CONSTRUCTORS ---

	public ResponseTime() {
	}

	public ResponseTime(String headerName) {
		setHeaderName(headerName);
	}

	// --- CREATE NEW PROCESSOR ---

	@Override
	public RequestProcessor install(RequestProcessor next, Tree config) {
		return new RequestProcessor() {

			/**
			 * Handles request of the HTTP client.
			 * 
			 * @param req
			 *            WebRequest object that contains the request the client
			 *            made of the ApiGateway
			 * @param rsp
			 *            WebResponse object that contains the response the
			 *            ApiGateway returns to the client
			 * 
			 * @throws Exception
			 *             if an input or output error occurs while the
			 *             ApiGateway is handling the HTTP request
			 */
			@Override
			public void service(WebRequest req, WebResponse rsp) throws Exception {

				// Invoke next handler / action
				next.service(req, new WebResponse() {

					// First body
					AtomicBoolean firstBody = new AtomicBoolean(true);

					// Start time
					long start = System.currentTimeMillis();
					
					// Response finished
					AtomicBoolean finished = new AtomicBoolean();

					@Override
					public final void setStatus(int code) {
						rsp.setStatus(code);
					}

					@Override
					public final int getStatus() {
						return rsp.getStatus();
					}

					@Override
					public final void setHeader(String name, String value) {
						rsp.setHeader(name, value);
					}

					@Override
					public final String getHeader(String name) {
						return rsp.getHeader(name);
					}

					@Override
					public final void send(byte[] bytes) throws IOException {
						if (firstBody.compareAndSet(true, false)) {
							long duration = System.currentTimeMillis() - start;
							rsp.setHeader(headerName, duration + "ms");
						}
						rsp.send(bytes);
					}

					@Override
					public final boolean end() {
						if (finished.compareAndSet(false, true)) {
							return rsp.end();
						}
						return false;
					}

					@Override
					public final void setProperty(String name, Object value) {
						rsp.setProperty(name, value);
					}

					@Override
					public final Object getProperty(String name) {
						return rsp.getProperty(name);
					}

				});
			}
		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public String getHeaderName() {
		return headerName;
	}

	public void setHeaderName(String headerName) {
		this.headerName = Objects.requireNonNull(headerName);
	}

}