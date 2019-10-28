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
package services.moleculer.web.middleware;

import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import services.moleculer.eventbus.Matcher;
import services.moleculer.service.Name;
import services.moleculer.util.FastBuildTree;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * The IpFilter Middleware adds the ability to allow or block requests based on
 * the IP address of the client. Sample:
 * <pre>
 * IpFilter filter = new IpFilter();
 * filter.allow("150.10.**");
 * route.use(filter);
 * </pre>
 */
@Name("IP Address Filter")
public class IpFilter extends HttpMiddleware implements HttpConstants {

	// --- LOGGER ---

	protected final Logger logger = LoggerFactory.getLogger(getClass());
	
	// --- PROPERTIES ---

	/**
	 * Template path of the HTML response.
	 */
	protected String htmlTemplatePath;

	/**
	 * Template of the HTML response.
	 */
	protected String htmlTemplate = "<html><head><meta charset=\"utf-8\"/><title>Error 403</title><style>html{height:100%}"
			+ "body{color:#888;margin:0}#main{display:table;width:100%;height:100vh;text-align:center}"
			+ ".x{display:table-cell;vertical-align:middle}.x h1{font-size:50px}.x h2{font-size:25px}"
			+ "</style></head><body><div id=\"main\"><div class=\"x\"><h1>Error 403</h1><h2>"
			+ "Access forbidden by rule.</h2></div></div></body></html>";

	/**
	 * Cached response bytes.
	 */
	protected byte[] htmlTemplateBytes;

	/**
	 * Masks (allowed addresses, eg "255.12.34.*").
	 */
	protected String[] allow;

	/**
	 * Masks (denied addresses, eg "255.10.**").
	 */
	protected String[] deny;

	// --- CONSTRUCTORS ---

	public IpFilter() {
	}

	public IpFilter(String... allow) {
		allow(allow);
	}

	// --- CREATE NEW PROCESSOR ---

	@Override
	public RequestProcessor install(RequestProcessor next, Tree config) {
		return new AbstractRequestProcessor(next) {

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
				String address = getAddress(req);
				if (address == null) {

					// Missing client address?
					sendForbidden(req, rsp, address);
					return;
				}

				// Check "deny" masks
				if (deny != null) {
					if (deny.length == 1) {
						if (Matcher.matches(address, deny[0])) {

							// IP is denied
							sendForbidden(req, rsp, address);
							return;
						}
					} else {
						for (String pattern : deny) {
							if (Matcher.matches(address, pattern)) {

								// IP is denied
								sendForbidden(req, rsp, address);
								return;
							}
						}
					}
				}

				// Check "allow" masks
				if (allow != null) {
					if (allow.length == 1) {
						if (Matcher.matches(address, allow[0])) {

							// Continue processing (access granted)
							next.service(req, rsp);
							return;
						}
					} else {
						for (String pattern : allow) {
							if (Matcher.matches(address, pattern)) {

								// Continue processing (access granted)
								next.service(req, rsp);
								return;
							}
						}
					}

					// IP is not allowed
					sendForbidden(req, rsp, address);
					return;
				}

				// Continue processing
				next.service(req, rsp);
			}
		};
	}

	protected String getAddress(WebRequest req) {
		
		// Return IP address
		return req.getAddress();
	}
	
	protected void sendForbidden(WebRequest req, WebResponse rsp, String address) {
		try {

			// Set 403 Forbidden status
			rsp.setStatus(403);

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
				body.putUnsafe("message", "Forbidden");
				bytes = body.toBinary();

			} else {

				// Response in HTML format
				rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_HTML);
				if (htmlTemplateBytes == null) {
					if (htmlTemplate == null && htmlTemplatePath != null) {
						htmlTemplate = new String(readAllBytes(htmlTemplatePath), StandardCharsets.UTF_8);
					}
					htmlTemplateBytes = htmlTemplate.getBytes(StandardCharsets.UTF_8);
				}
				bytes = htmlTemplateBytes;
			}
			rsp.setHeader(CACHE_CONTROL, NO_CACHE);
			rsp.setHeader(CONTENT_LENGTH, Integer.toString(bytes.length));
			rsp.send(bytes);
		} catch (IOException closed) {

			// Ignored

		} finally {
			rsp.end();
		}
		String info = address.isEmpty() ? "<unknown>" : address;
		logger.warn("Access denied for client address " + info + "!");
	}

	// --- ADD ALLOW/DENY FILTER ---

	/**
	 * Adds "allow" filters (eg "255.10.**", "230.110.10.*" or regular
	 * expression).
	 * 
	 * @param filters
	 *            array of filters
	 * 
	 * @return this IpFilter instance
	 */
	public IpFilter allow(String... filters) {
		
		// Merge filters
		allow = mergeFilters(allow, filters);

		// Return this (for method chaining)
		return this;
	}

	/**
	 * Adds "deny" filters (eg "255.10.**", "130.30.12.*" or regular
	 * expression).
	 * 
	 * @param filters
	 *            array of filters
	 * 
	 * @return this IpFilter instance
	 */
	public IpFilter deny(String... filters) {

		// Merge filters
		deny = mergeFilters(deny, filters);

		// Return this (for method chaining)
		return this;
	}

	protected String[] mergeFilters(String[] previous, String[] filters) {
		if (filters == null || filters.length == 0) {
			return previous;
		}
		HashSet<String> set = new HashSet<>();
		if (previous != null) {
			set.addAll(Arrays.asList(previous));
		}
		for (String filter : filters) {
			if (filter != null) {
				filter = filter.trim();
				if (!filter.isEmpty()) {
					set.add(filter);
				}
			}
		}
		if (set.isEmpty()) {
			return null;
		}
		String[] array = new String[set.size()];
		set.toArray(array);
		return array;
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
		this.htmlTemplateBytes = null;
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
		this.htmlTemplateBytes = null;
	}

}
