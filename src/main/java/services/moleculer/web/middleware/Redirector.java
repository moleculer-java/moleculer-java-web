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

import static services.moleculer.util.CommonUtils.formatPath;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.datatree.Tree;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * Redirects all requests to the specified URL / location.
 */
@Name("Redirector")
public class Redirector extends HttpMiddleware implements HttpConstants {

	// --- PROPERTIES ---

	/**
	 * 307 = Temporary Redirect.
	 */
	protected int status = 307;

	/**
	 * Path to redirect (null = redirects all requests).
	 */
	protected String path;
	
	/**
	 * Path to redirect (formatted and checked).
	 */
	protected String formattedPath;
	
	/**
	 * URL of the redirection.
	 */
	protected String location = "/";

	/**
	 * Template of the HTML response.
	 */
	protected String htmlTemplate = "<html><head><meta http-equiv=\"Refresh\" content=\"0; url={location}"
			+ "\" /></head><body>This page has moved to <a href=\"{location}\">{location}</a>.</body></html>";

	// --- CONSTRUCTORS ---

	public Redirector() {
	}

	public Redirector(String location) {
		setLocation(location);
	}

	public Redirector(String location, int status) {
		setLocation(location);
		setStatus(status);
	}

	public Redirector(String path, String location, int status) {
		setPath(path);
		setLocation(location);
		setStatus(status);
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

					// Check path
					if (formattedPath != null && !formattedPath.equals(req.getPath())) {
						next.service(req, rsp);
						return;
					}
					
					// Create HTML body
					String body = htmlTemplate.replace("{location}", location);
					byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

					// Send status code and "Location" header
					rsp.setStatus(status);
					rsp.setHeader(LOCATION, location);
					rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_HTML);
					rsp.setHeader(CONTENT_LENGTH, Integer.toString(bytes.length));
					
					// Send HTML body
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
	 * @return the location
	 */
	public String getLocation() {
		return location;
	}

	/**
	 * @param location
	 *            the location to set
	 */
	public void setLocation(String location) {
		this.location = Objects.requireNonNull(location);
	}

	/**
	 * @return the status
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * @param status
	 *            the status to set
	 */
	public void setStatus(int status) {
		this.status = status;
	}

	/**
	 * @return the path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @param path
	 *            the path to set
	 */
	public void setPath(String path) {
		this.path = path;
		if (path.isEmpty()) {
			formattedPath = "/";
		} else {
			formattedPath = formatPath(path);			
		}
	}

}