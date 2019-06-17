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

import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import io.datatree.Tree;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

@Name("Favicon")
public class Favicon extends HttpMiddleware implements HttpConstants {

	// --- PROPERTIES ---

	protected String iconPath;
	protected int maxAge;

	// --- CACHED IMAGE ---

	protected byte[] image;

	// --- CONSTRUCTORS ---

	public Favicon() {
		this("favicon.ico");
	}

	public Favicon(String pathToIcon) {
		setIconPath(pathToIcon);
	}

	public Favicon(String pathToIcon, int maxAge) {
		setIconPath(pathToIcon);
		setMaxAge(maxAge);
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
				if ("/favicon.ico".equals(req.getPath())) {
					try {
						rsp.setHeader(CONTENT_TYPE, "image/x-icon");						
						if (maxAge > 0) {
							rsp.setHeader(CACHE_CONTROL, "public, max-age=" + maxAge);
						}
						if (image == null && iconPath != null) {
							image = readAllBytes(iconPath);
							if (image.length == 0) {
								throw new IllegalArgumentException("File or resource not found: " + iconPath);
							}
						}
						rsp.setHeader(CONTENT_LENGTH, Integer.toString(image.length));
						rsp.send(image);
					} finally {
						rsp.end();
					}
					return;					
				}
				
				// Invoke next handler / action
				next.service(req, rsp);
			}

		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public String getIconPath() {
		return iconPath;
	}

	public void setIconPath(String iconPath) {
		this.image = null;
		this.iconPath = iconPath;
	}

	public int getMaxAge() {
		return maxAge;
	}

	public void setMaxAge(int maxAge) {
		this.maxAge = maxAge;
	}

}