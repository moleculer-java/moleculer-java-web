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
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

@Name("Not Found")
public class NotFound extends HttpMiddleware implements HttpConstants {

	// --- PROPERTIES ---

	/**
	 * Use HTML or JSON response (true = HTML).
	 */
	protected boolean sendHtmlResponse;

	/**
	 * Template of the HTML response.
	 */
	protected String htmlTemplate = "<html><body><h1>404 - Not found</h1><h2>Path: {path}</h2></body></html>";
	
	// --- CONSTRUCTORS ---

	public NotFound() {
	}

	public NotFound(boolean sendHtmlResponse) {
		setSendHtmlResponse(sendHtmlResponse);
	}

	public NotFound(boolean sendHtmlResponse, String htmlTemplate) {
		setSendHtmlResponse(sendHtmlResponse);
		setHtmlTemplate(htmlTemplate);
	}

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
					byte[] bytes;
					if (sendHtmlResponse) {
						
						// Response in HTML format
						rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_HTML);
						String body = htmlTemplate.replace("{path}", path);
						bytes = body.getBytes(StandardCharsets.UTF_8);
						
					} else {
						
						// Response in JSON format
						rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_JSON);
						Tree body = new Tree();
						body.put("success", false);
						body.put("message", "Not Found: " + path);
						bytes = body.toBinary();
						
					}
					rsp.setHeader(CONTENT_LENGTH, Integer.toString(bytes.length));
					rsp.send(bytes);
				} finally {
					rsp.end();
				}
			}
		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	/**
	 * @return the htmlTemplate
	 */
	public String getHtmlTemplate() {
		return htmlTemplate;
	}

	/**
	 * @param htmlTemplate the htmlTemplate to set
	 */
	public void setHtmlTemplate(String htmlTemplate) {
		this.htmlTemplate = htmlTemplate;
	}

	/**
	 * @return the sendHtmlResponse
	 */
	public boolean isSendHtmlResponse() {
		return sendHtmlResponse;
	}

	/**
	 * @param sendHtmlResponse the sendHtmlResponse to set
	 */
	public void setSendHtmlResponse(boolean sendHtmlResponse) {
		this.sendHtmlResponse = sendHtmlResponse;
	}

}