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

import io.datatree.Tree;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

@Name("CORS Headers")
public class CorsHeaders extends HttpMiddleware implements HttpConstants {

	// --- PROPERTIES ---

	/**
	 * The Access-Control-Allow-Origin CORS header
	 */
	protected String origin = "*";

	/**
	 * The Access-Control-Allow-Methods CORS header
	 */
	protected String methods = "GET,OPTIONS,POST,PUT,DELETE";

	/**
	 * The Access-Control-Allow-Headers CORS header
	 */
	protected String allowedHeaders;

	/**
	 * The Access-Control-Expose-Headers CORS header
	 */
	protected String exposedHeaders;

	/**
	 * The Access-Control-Allow-Credentials CORS header
	 */
	protected boolean credentials;

	/**
	 * The Access-Control-Max-Age CORS header
	 */
	protected int maxAge;

	// --- CONSTRUCTORS ---

	public CorsHeaders() {
	}

	public CorsHeaders(String origin) {
		setOrigin(origin);
	}

	public CorsHeaders(String origin, String methods) {
		setOrigin(origin);
		setMethods(methods);
	}

	public CorsHeaders(String origin, String methods, int maxAge) {
		setOrigin(origin);
		setMethods(methods);
		setMaxAge(maxAge);
	}

	public CorsHeaders(String origin, String methods, String allowedHeaders, String exposedHeaders, boolean credentials,
			int maxAge) {
		setOrigin(origin);
		setMethods(methods);
		setAllowedHeaders(allowedHeaders);
		setExposedHeaders(exposedHeaders);
		setCredentials(credentials);
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

				// Add the Access-Control-Allow-Origin header
				if (origin != null) {
					rsp.setHeader("Access-Control-Allow-Origin", origin);
				}

				// Add the Access-Control-Allow-Methods header
				if (methods != null) {
					rsp.setHeader("Access-Control-Allow-Methods", methods);
				}

				// Add the Access-Control-Allow-Headers header
				if (allowedHeaders != null) {
					rsp.setHeader("Access-Control-Allow-Headers", allowedHeaders);
				}

				// Add the Access-Control-Expose-Headers header
				if (exposedHeaders != null) {
					rsp.setHeader("Access-Control-Expose-Headers", exposedHeaders);
				}

				// Add the Access-Control-Allow-Credentials header
				rsp.setHeader("Access-Control-Allow-Credentials", Boolean.toString(credentials));

				// Add the Access-Control-Max-Age header
				if (maxAge > 0) {
					rsp.setHeader("Access-Control-Max-Age", Integer.toString(maxAge));
				}
				
				// Invoke next handler
				next.service(req, rsp);
			}

		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getMethods() {
		return methods;
	}

	public void setMethods(String methods) {
		this.methods = methods;
	}

	public String getAllowedHeaders() {
		return allowedHeaders;
	}

	public void setAllowedHeaders(String allowedHeaders) {
		this.allowedHeaders = allowedHeaders;
	}

	public String getExposedHeaders() {
		return exposedHeaders;
	}

	public void setExposedHeaders(String exposedHeaders) {
		this.exposedHeaders = exposedHeaders;
	}

	public boolean isCredentials() {
		return credentials;
	}

	public void setCredentials(boolean credentials) {
		this.credentials = credentials;
	}

	public int getMaxAge() {
		return maxAge;
	}

	public void setMaxAge(int maxAge) {
		this.maxAge = maxAge;
	}

}