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

import static services.moleculer.web.common.GatewayUtils.getFileURL;
import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import java.nio.charset.StandardCharsets;

import io.datatree.Tree;
import services.moleculer.service.Name;
import services.moleculer.util.FastBuildTree;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * Refuses all requests with "Error 400 Not Found" message.
 */
@Name("Not Found")
public class NotFound extends HttpMiddleware implements HttpConstants {

	// --- PROPERTIES ---

	/**
	 * Template path of the HTML response.
	 */
	protected String htmlTemplatePath;

	/**
	 * Template of the HTML response.
	 */
	protected String htmlTemplate = "<html><head><meta charset=\"utf-8\"/><title>Error 404</title><style>html{height:100%}"
			+ "body{color:#888;margin:0}#main{display:table;width:100%;height:100vh;text-align:center}"
			+ ".x{display:table-cell;vertical-align:middle}.x h1{font-size:50px}.x h2{font-size:25px}"
			+ "</style></head><body><div id=\"main\"><div class=\"x\"><h1>Error 404</h1><h2>"
			+ "This page does not exist: {path}</h2></div></div></body></html>";

	// --- CONSTRUCTORS ---

	public NotFound() {
	}

	public NotFound(String htmlTemplateOrPath) {
		if (getFileURL(htmlTemplateOrPath) == null) {
			setHtmlTemplate(htmlTemplateOrPath);
		} else {
			setHtmlTemplatePath(htmlTemplateOrPath);
		}
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
				try {

					// Get path
					String path = req.getPath();

					// Set 404 Not Found status
					rsp.setStatus(404);

					// Detect Accept-Encoding
					String accept = req.getHeader(ACCEPT);
					boolean sendJSON = accept != null && accept.contains("json");

					// Send body
					byte[] bytes;
					if (sendJSON) {

						// Response in JSON format
						rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_JSON);
						FastBuildTree body = new FastBuildTree(2);
						body.putUnsafe("success", false);
						body.putUnsafe("message", "Not Found: " + path);
						bytes = body.toBinary();

					} else {

						// Response in HTML format
						rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_HTML);
						if (htmlTemplate == null && htmlTemplatePath != null) {
							htmlTemplate = new String(readAllBytes(htmlTemplatePath), StandardCharsets.UTF_8);
						}
						String body = htmlTemplate.replace("{path}", path);
						bytes = body.getBytes(StandardCharsets.UTF_8);

					}
					rsp.setHeader(CACHE_CONTROL, NO_CACHE);
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
	 * @param htmlTemplate
	 *            the htmlTemplate to set
	 */
	public void setHtmlTemplate(String htmlTemplate) {
		this.htmlTemplate = htmlTemplate;
	}

	/**
	 * @return the htmlTemplatePath
	 */
	public String getHtmlTemplatePath() {
		return htmlTemplatePath;
	}

	/**
	 * @param htmlTemplatePath
	 *            the htmlTemplatePath to set
	 */
	public void setHtmlTemplatePath(String htmlTemplatePath) {
		this.htmlTemplate = null;
		this.htmlTemplatePath = htmlTemplatePath;
	}

}