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

import java.nio.charset.StandardCharsets;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

@Name("Not Found")
public class NotFound extends HttpMiddleware implements HttpConstants {

	// --- JSON / HTML RESPONSE ---

	protected boolean htmlResponse;

	// --- CREATE NEW PROCESSOR ---

	@Override
	public RequestProcessor install(RequestProcessor next, Tree config) {
		return new RequestProcessor() {

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
				try {

					// Get path
					String path = req.getPath();

					// 404 Not Found
					rsp.setStatus(404);
					if (htmlResponse) {
						rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_HTML);

						StringBuilder body = new StringBuilder(512);
						body.append("<html><body><h1>404 - Not found</h1><h2>");
						body.append(path);
						body.append("</h2><hr/>");
						body.append("Moleculer V");
						body.append(ServiceBroker.SOFTWARE_VERSION);
						body.append("</body></html>");

						rsp.send(body.toString().getBytes(StandardCharsets.UTF_8));
					} else {
						rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_JSON);

						Tree body = new Tree();
						body.put("success", false);
						body.put("message", "Not Found: " + path);

						rsp.send(body.toBinary());
					}
				} finally {
					rsp.end();
				}
			}

		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public boolean isHtmlResponse() {
		return htmlResponse;
	}

	public void setHtmlResponse(boolean htmlResponse) {
		this.htmlResponse = htmlResponse;
	}

}