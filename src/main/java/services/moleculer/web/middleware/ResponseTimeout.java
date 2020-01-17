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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.datatree.Tree;
import services.moleculer.service.Name;
import services.moleculer.util.FastBuildTree;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * Middleware that will timeout requests if the response has not been written
 * after the specified time. HTTP response code will be "408". Sample:
 * <pre>
 * route.use(new ResponseTimeout(1000L * 30));
 * </pre>
 */
@Name("Response Timeout")
public class ResponseTimeout extends HttpMiddleware implements HttpConstants {

	// --- PROPERTIES ---

	/**
	 * Request timeout in MILLISECONDS (0 = no timeout / disable timer).
	 */
	protected long timeout = 5000;

	/**
	 * Response status (408 = Request Timeout).
	 */
	protected int status = 408;

	/**
	 * Template of the HTML response.
	 */
	protected String htmlTemplate = "<html><head><meta charset=\"utf-8\"/><title>Error {status}</title><style>html{height:100%}"
			+ "body{color:#888;margin:0}#main{display:table;width:100%;height:100vh;text-align:center}"
			+ ".x{display:table-cell;vertical-align:middle}.x h1{font-size:50px}.x h2{font-size:25px}"
			+ "</style></head><body><div id=\"main\"><div class=\"x\"><h1>Error {status}</h1><h2>"
			+ "Request Timeouted</h2></div></div></body></html>";

	/**
	 * Template path of the HTML response.
	 */
	protected String htmlTemplatePath;

	// --- MOLECULER COMPONENTS ---

	protected ScheduledExecutorService scheduler;

	// --- MESSAGE CACHES ---

	protected byte[] cachedHTML;
	protected byte[] cachedJSON;

	// --- CONSTRUCTORS ---

	public ResponseTimeout() {
		this(0);
	}

	public ResponseTimeout(long timeoutMillis) {
		setTimeout(timeoutMillis);
		refreshCaches();
	}

	public ResponseTimeout(long timeoutMillis, int status, String htmlTemplateOrPath) {
		if (getFileURL(htmlTemplateOrPath) == null) {
			setHtmlTemplate(htmlTemplateOrPath);
		} else {
			setHtmlTemplatePath(htmlTemplateOrPath);
		}
		this.status = status;
		setTimeout(timeoutMillis);
	}

	// --- START MIDDLEWARE ---

	public void started(services.moleculer.ServiceBroker broker) throws Exception {
		super.started(broker);
		scheduler = broker.getConfig().getScheduler();
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

				// Start timer
				ScheduledFuture<?> future = timeout > 0 ? scheduler.schedule(() -> {
					try {

						// Set 408 status
						rsp.setStatus(status);

						// Detect Accept-Encoding
						String accept = req.getHeader(ACCEPT);
						boolean sendJSON = accept != null && accept.contains("json");

						// Load template
						if (htmlTemplate == null && htmlTemplatePath != null) {
							htmlTemplate = new String(readAllBytes(htmlTemplatePath), StandardCharsets.UTF_8);
							refreshCaches();
						}

						// Create body
						byte[] bytes;
						if (sendJSON) {

							// Response in JSON format
							rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_JSON);
							bytes = cachedJSON;

						} else {

							// Response in HTML format
							rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_HTML);
							bytes = cachedHTML;

						}

						// Set Content-Length
						rsp.setHeader(CONTENT_LENGTH, Integer.toString(bytes.length));

						// Send message body
						rsp.send(bytes);

					} catch (Exception cause) {
						logger.error("Unable to send timeout message to client!", cause);
					} finally {
						rsp.end();
					}
				}, timeout, TimeUnit.MILLISECONDS) : null;

				// Invoke next handler / action
				next.service(req, new WebResponse() {

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
						rsp.send(bytes);
					}

					@Override
					public final boolean end() {
						if (finished.compareAndSet(false, true)) {
							if (future != null) {
								future.cancel(false);
							}
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

					@Override
					public final Object getInternalObject() {
						return rsp.getInternalObject();
					}
					
				});
			}
		};
	}

	// --- REFRESH CACHED MESSAGES ---

	protected void refreshCaches() {
		FastBuildTree json = new FastBuildTree(2);
		json.putUnsafe("success", false);
		json.putUnsafe("message", "Request Timeouted");
		cachedJSON = json.toBinary();
		String html = htmlTemplate.replace("{status}", Integer.toString(status));
		cachedHTML = html.getBytes(StandardCharsets.UTF_8);
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

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
		htmlTemplatePath = null;
		refreshCaches();
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
		refreshCaches();
	}

}