/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2018 Andras Berkes [andras.berkes@programmer.net]<br>
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
package services.moleculer.web.router;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import io.datatree.dom.TreeWriter;
import io.datatree.dom.TreeWriterRegistry;
import services.moleculer.context.CallOptions;
import services.moleculer.context.CallOptions.Options;
import services.moleculer.service.ServiceInvoker;
import services.moleculer.stream.PacketStream;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;
import services.moleculer.web.template.AbstractTemplateEngine;

import static services.moleculer.web.common.GatewayUtils.sendError;

public class ActionInvoker implements RequestProcessor, HttpConstants {

	// --- CONSTANTS ---
	
	protected static final byte[] EMPTY_RESPONSE = "{}".getBytes(); 
	
	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(ActionInvoker.class);

	// --- PROPERTIES ---

	protected final String actionName;
	protected final String pathPattern;
	protected final boolean isStatic;
	protected final String pathPrefix;
	protected final int[] indexes;
	protected final String[] names;
	protected final CallOptions.Options opts;

	// --- MOLECULER COMPONENTS ---

	protected final ServiceInvoker serviceInvoker;

	// --- JSON SERIALIZER ---

	protected final TreeWriter jsonSerializer;

	// --- TEMPLATE ENGINE ---

	protected final AbstractTemplateEngine abstractTemplateEngine;

	// --- CONSTRUCTOR ---

	public ActionInvoker(String actionName, String pathPattern, boolean isStatic, String pathPrefix, int[] indexes,
			String[] names, Options opts, ServiceInvoker serviceInvoker, AbstractTemplateEngine abstractTemplateEngine) {
		this.actionName = actionName;
		this.pathPattern = pathPattern;
		this.isStatic = isStatic;
		this.pathPrefix = pathPrefix;
		this.indexes = indexes;
		this.names = names;
		this.opts = opts;
		this.serviceInvoker = serviceInvoker;
		this.abstractTemplateEngine = abstractTemplateEngine;
		this.jsonSerializer = TreeWriterRegistry.getWriter(null);
	}

	// --- PROCESS (SERVLET OR NETTY) HTTP REQUEST ---

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

		// Disable cache
		rsp.setHeader(CACHE_CONTROL, NO_CACHE);

		// Parse URL
		Tree params = null;
		if (isStatic) {

			// HTTP GET QueryString
			String query = req.getQuery();
			if (query != null && !query.isEmpty()) {
				params = parseQueryString(query);
			}
		} else {

			// Parameters in URL (eg "/path/:id/:name")
			params = new Tree();
			String[] tokens = pathPattern.split("/");
			for (int i = 0; i < indexes.length; i++) {
				params.put(names[i], tokens[i]);
			}
		}

		// Multipart request
		if (req.isMultipart()) {
			if (params == null) {
				params = new Tree();
			}

			// Invoke service
			serviceInvoker.call(actionName, params, opts, req.getBody(), null).then(out -> {
				sendResponse(rsp, out);
			}).catchError(cause -> {
				sendError(rsp, cause);
			});
			return;

		}

		// GET without body
		int contentLength = req.getContentLength();
		if (contentLength == 0) {

			// Invoke service
			serviceInvoker.call(actionName, params, opts, null, null).then(out -> {
				sendResponse(rsp, out);
			}).catchError(cause -> {
				logger.error("Unable to invoke action!", cause);
				sendError(rsp, cause);
			});
			return;
		}

		// POST with JSON / QueryString body
		byte[] body = new byte[contentLength];
		AtomicInteger pos = new AtomicInteger();
		AtomicBoolean faulty = new AtomicBoolean();
		final Tree urlParams = params;
		req.getBody().onPacket((bytes, cause, close) -> {
			if (bytes != null) {
				System.arraycopy(bytes, 0, body, pos.getAndAdd(bytes.length), bytes.length);
			} else if (cause != null) {
				faulty.set(true);
				logger.error("Unexpected error occured while receiving and parsing client request!", cause);
				sendError(rsp, cause);
			}
			if (close && !faulty.get()) {

				// Parse body
				Tree postParams = parsePostBody(body);

				// Merge with URL parameters
				if (urlParams != null) {
					postParams.copyFrom(urlParams, true);
				}

				// Invoke service
				serviceInvoker.call(actionName, postParams, opts, null, null).then(out -> {
					sendResponse(rsp, out);
				}).catchError(err -> {
					logger.error("Unable to invoke action!", err);
					sendError(rsp, err);
				});
			}
		});
	}

	// --- PARSE BODY OF THE GET / POST REQUEST ---

	protected Tree parsePostBody(byte[] bytes) throws Exception {
		Tree params;
		if (bytes.length == 0) {
			params = new Tree();
		} else {
			if (bytes[0] == '{' || bytes[0] == '[') {

				// JSON body
				params = new Tree(bytes);

			} else {

				// QueryString body
				params = parseQueryString(new String(bytes, StandardCharsets.UTF_8));
			}
		}
		return params;
	}

	// --- PARSE HTTP QUERY STRING ---

	protected Tree parseQueryString(String query) throws UnsupportedEncodingException {
		Tree params = new Tree();
		String[] pairs = query.split("&");
		int i;
		for (String pair : pairs) {
			i = pair.indexOf("=");
			if (i > -1) {
				params.put(URLDecoder.decode(pair.substring(0, i), "UTF-8"),
						URLDecoder.decode(pair.substring(i + 1), "UTF-8"));
			}
		}
		return params;
	}

	// --- SEND JSON/STREAMED RESPONSE ---

	protected void sendResponse(WebResponse rsp, Tree out) throws Exception {

		// Disable cache
		rsp.setHeader(CACHE_CONTROL, NO_CACHE);
		if (out == null) {
			try {
				rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_JSON);
				rsp.setHeader(CONTENT_LENGTH, "2");
				rsp.send(EMPTY_RESPONSE);
			} finally {
				rsp.end();
			}
			return;
		}

		// Set headers from "meta"
		String templatePath = null;
		boolean contentTypeSet = false;
		Tree meta = out.getMeta(false);
		if (meta != null) {

			// Path of the HTML-template
			if (abstractTemplateEngine != null) {
				templatePath = meta.get(META_TEMPLATE, (String) null);
			}

			// Temporary Redirect
			String value = meta.get(META_LOCATION, (String) null);
			if (value != null && !value.isEmpty()) {
				rsp.setStatus(307);
				rsp.setHeader(LOCATION, value);
			}

			// Status code
			value = meta.get(META_STATUS, (String) null);
			if (value != null && !value.isEmpty()) {
				rsp.setStatus(Integer.parseInt(value));
			}

			// Content type
			value = meta.get(META_CONTENT_TYPE, (String) null);
			if (value != null && !value.isEmpty()) {
				rsp.setHeader(CONTENT_TYPE, value);
				contentTypeSet = true;
			}

			// Custom headers
			Tree headers = meta.get(META_HEADERS);
			if (headers != null) {
				String name;
				for (Tree header : headers) {
					name = header.getName();
					value = header.asString();
					if (name != null && !name.isEmpty() && value != null && !value.isEmpty()) {
						rsp.setHeader(name, value);
						if (CONTENT_TYPE.equals(name)) {
							contentTypeSet = true;
						}
					}
				}
			}
		}

		// Send body
		Object object = out.asObject();
		if (object != null && object instanceof PacketStream) {

			// Stream type?
			if (!contentTypeSet) {
				rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_JSON);
			}

			// Streamed response (large file, media, etc.)
			PacketStream stream = (PacketStream) object;
			stream.onPacket((bytes, cause, close) -> {
				AtomicBoolean failed = new AtomicBoolean();
				if (bytes != null) {
					rsp.send(bytes);
				} else if (cause != null) {
					failed.set(true);
					logger.error("Unexpected error occured while streaming data to client!", cause);
					sendError(rsp, cause);
					return;
				}
				if (close && !failed.get()) {
					rsp.end();
				}
			});

		} else {

			// Tree (JSON) body
			byte[] body;
			try {

				// TODO Invoke AfterCallProcessor

				if (templatePath != null && !templatePath.isEmpty()) {

					// Content-type is HTML
					if (!contentTypeSet) {
						rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_HTML);
					}

					// Invoke TemplateEngine
					body = abstractTemplateEngine.transform(templatePath, out);

				} else {

					// Content-type is JSON
					if (!contentTypeSet) {
						rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_JSON);
					}

					// Serialize
					body = jsonSerializer.toBinary(out.asObject(), null, false);
				}
			} catch (Throwable cause) {
				logger.error("Unable to serialize response!", cause);
				sendError(rsp, cause);
				return;
			}
			try {
				rsp.setHeader(CONTENT_LENGTH, body == null ? "0" : Integer.toString(body.length));
				rsp.send(body);
			} finally {
				rsp.end();
			}
		}
	}

}