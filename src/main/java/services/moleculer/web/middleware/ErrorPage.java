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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.datatree.Tree;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * Custom error page (Error 404, 500, etc.) handler.
 */
@Name("Error Handler")
public class ErrorPage extends HttpMiddleware implements HttpConstants {

	// --- PROPERTIES ---

	/**
	 * Template of the HTML response.
	 */
	protected String htmlTemplate = "<html><head><meta charset=\"utf-8\"/><title>Error {status}</title><style>html{height:100%}"
			+ "body{color:#888;margin:0}#main{display:table;width:100%;height:100vh;text-align:center}"
			+ ".x{display:table-cell;vertical-align:middle}.x h1{font-size:50px}.x h2{font-size:25px}"
			+ "</style></head><body><div id=\"main\"><div class=\"x\"><h1>Error {status}</h1><h2>"
			+ "{message}</h2></div></div></body></html>";

	/**
	 * Template path of the HTML response.
	 */
	protected String htmlTemplatePath;

	/**
	 * Optional status-specific templates (eg. 404 -> 404.html, 500 -> 500.html)
	 */
	protected HashMap<Integer, String> statusSpecificTemplates = new HashMap<>();

	// --- CONSTRUCTORS ---

	public ErrorPage() {
	}

	public ErrorPage(String htmlTemplateOrPath) {
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

				AtomicInteger status = new AtomicInteger();
				LinkedHashMap<String, String> headers = new LinkedHashMap<>();
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				AtomicBoolean headersSent = new AtomicBoolean();
				AtomicBoolean finished = new AtomicBoolean();

				// Invoke next handler / action
				next.service(req, new WebResponse() {

					@Override
					public final void setStatus(int code) {
						rsp.setStatus(code);
						status.set(code);
					}

					@Override
					public final int getStatus() {
						return rsp.getStatus();
					}

					@Override
					public final void setHeader(String name, String value) {
						headers.put(name, value);
					}

					@Override
					public final String getHeader(String name) {
						return headers.get(name);
					}

					@Override
					public final void send(byte[] bytes) throws IOException {
						if (status.get() < 400) {

							// Send headers
							if (headersSent.compareAndSet(false, true)) {
								for (Map.Entry<String, String> entry : headers.entrySet()) {
									rsp.setHeader(entry.getKey(), entry.getValue());
								}
							}

							// Send directly
							rsp.send(bytes);

						} else {

							// Store response (send later)
							buffer.write(bytes);
						}
					}

					@Override
					public final boolean end() {
						if (finished.compareAndSet(false, true)) {
							int code = status.get();
							if (code < 400) {

								// Unmodified response
								return rsp.end();

							}

							// Create body
							byte[] bytes = buffer.toByteArray();
							String stack = "[absent]";
							String message = null;
							try {
								if (bytes[0] == '{' || bytes[0] == '[') {
									Tree t = new Tree(bytes);
									message = t.get("message", (String) null);
									stack = t.get("stack", "[absent]");
								}
							} catch (Throwable ignored) {
							}

							// Create message
							if (message == null || message.isEmpty()) {
								switch (code) {
								case 400:
									message = "Bad Request";
									break;
								case 401:
									message = "Unauthorized";
									break;
								case 402:
									message = "Payment Required";
									break;
								case 403:
									message = "Forbidden";
									break;
								case 404:
									message = "Not Found";
									break;
								case 405:
									message = "Method Not Allowed";
									break;
								case 406:
									message = "Not Acceptable";
									break;
								case 407:
									message = "Proxy Authentication Required";
									break;
								case 408:
									message = "Request Timeout";
									break;
								case 409:
									message = "Conflict";
									break;
								case 410:
									message = "Gone";
									break;
								case 411:
									message = "Length Required";
									break;
								case 412:
									message = "Precondition Failed";
									break;
								case 413:
									message = "Request Entity Too Large";
									break;
								case 414:
									message = "Request-URI Too Long";
									break;
								case 415:
									message = "Unsupported Media Type";
									break;
								case 416:
									message = "Requested range not satisfiable";
									break;
								case 417:
									message = "Expectation Failed";
									break;
								case 418:
									message = "I'm a teapot";
									break;
								case 419:
									message = "Insufficient Space On Resource";
									break;
								case 420:
									message = "Method Failure";
									break;
								case 421:
									message = "Destination Locked";
									break;
								case 422:
									message = "Unprocessable Entity";
									break;
								case 423:
									message = "Locked";
									break;
								case 424:
									message = "Failed Dependency";
									break;
								case 426:
									message = "Upgrade Required";
									break;
								case 428:
									message = "Precondition Required";
									break;
								case 429:
									message = "Too Many Requests";
									break;
								case 431:
									message = "Request Header Fields Too Large";
									break;
								case 451:
									message = "Unavailable For Legal Reasons";
									break;
								case 500:
									message = "Internal Server Error";
									break;
								case 501:
									message = "Not Implemented";
									break;
								case 502:
									message = "Bad Gateway";
									break;
								case 503:
									message = "Service Unavailable";
									break;
								case 504:
									message = "Gateway Timeout";
									break;
								case 505:
									message = "HTTP Version not supported";
									break;
								case 506:
									message = "Variant Also Negotiates";
									break;
								case 507:
									message = "Insufficient Storage";
									break;
								case 508:
									message = "Loop Detected";
									break;
								case 509:
									message = "Bandwidth Limit Exceeded";
									break;
								case 510:
									message = "Not Extended";
									break;
								case 511:
									message = "Network Authentication Required";
									break;
								default:
									message = "Unexpected Error Occured";
									break;
								}
							} else {

								// Detect Accept-Encoding
								String accept = req.getHeader(ACCEPT);
								if (accept != null && accept.contains("json")) {

									// Original message is JSON, and client
									// accepts JSON -> send original JSON
									boolean ok;
									try {
										rsp.send(bytes);
									} catch (Exception cause) {
										logger.error("Unable to send error message!", cause);
									} finally {
										ok = rsp.end();
									}
									return ok;
								}
							}

							// Find in status-specific templates
							String template = statusSpecificTemplates.get(code);

							// Use common HTML template
							if (htmlTemplate == null && htmlTemplatePath != null) {
								htmlTemplate = new String(readAllBytes(htmlTemplatePath), StandardCharsets.UTF_8);
							}
							if (template == null) {
								template = htmlTemplate;
							}

							// Create body
							String body = template.replace("{status}", Integer.toString(code))
									.replace("{message}", message).replace("{stack}", stack);
							bytes = body.getBytes(StandardCharsets.UTF_8);

							// Set headers
							headers.put(CONTENT_TYPE, CONTENT_TYPE_HTML);
							headers.put(CONTENT_LENGTH, Integer.toString(bytes.length));
							headers.put(CACHE_CONTROL, NO_CACHE);

							boolean ok;
							try {

								// Send headers
								for (Map.Entry<String, String> entry : headers.entrySet()) {
									rsp.setHeader(entry.getKey(), entry.getValue());
								}

								// Send body
								rsp.send(bytes);

							} catch (Exception cause) {
								logger.error("Unable to send error message!", cause);
							} finally {
								ok = rsp.end();
							}
							return ok;
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

	// --- STATUS-SPECIFIC TEMPLATES ---

	public ErrorPage setTemplate(int statusCode, String htmlTemplateOrPath) {
		if (getFileURL(htmlTemplateOrPath) == null) {
			statusSpecificTemplates.put(statusCode, htmlTemplateOrPath);
		} else {
			String template = new String(readAllBytes(htmlTemplateOrPath), StandardCharsets.UTF_8);
			statusSpecificTemplates.put(statusCode, template);
		}
		return this;
	}

	public HashMap<Integer, String> getStatusSpecificTemplates() {
		return statusSpecificTemplates;
	}

	public void setStatusSpecificTemplates(HashMap<Integer, String> statusSpecificTemplates) {
		this.statusSpecificTemplates = Objects.requireNonNull(statusSpecificTemplates);
	}

	// --- COMMON HTML TEMPLATE ---

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