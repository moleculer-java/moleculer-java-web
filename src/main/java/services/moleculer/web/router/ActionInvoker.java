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

import static services.moleculer.web.common.GatewayUtils.sendError;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import io.datatree.dom.TreeWriter;
import io.datatree.dom.TreeWriterRegistry;
import services.moleculer.config.ServiceBrokerConfig;
import services.moleculer.context.CallOptions;
import services.moleculer.context.CallOptions.Options;
import services.moleculer.eventbus.Eventbus;
import services.moleculer.context.Context;
import services.moleculer.service.ServiceInvoker;
import services.moleculer.stream.PacketStream;
import services.moleculer.uid.UidGenerator;
import services.moleculer.util.CheckedTree;
import services.moleculer.web.CallProcessor;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;
import services.moleculer.web.template.AbstractTemplateEngine;
import services.moleculer.web.template.languages.MessageLoader;

public class ActionInvoker implements RequestProcessor, HttpConstants {

	// --- CONSTANTS ---

	protected static final byte[] EMPTY_RESPONSE = "{}".getBytes();

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(ActionInvoker.class);

	// --- PROPERTIES ---

	protected final String actionName;
	protected final String pathPattern;
	protected final String pathPrefix;
	protected final int[] indexes;
	protected final String[] names;
	protected final CallOptions.Options opts;
	protected final String nodeID;

	// --- INTERNAL OBJECTS ---

	protected final ServiceInvoker serviceInvoker;
	protected final TreeWriter jsonSerializer;
	protected final Route route;
	protected final CallProcessor beforeCall;
	protected final CallProcessor afterCall;
	protected final ExecutorService executor;
	protected final AbstractTemplateEngine templateEngine;
	protected final MessageLoader messageLoader;
	protected final Eventbus eventbus;
	protected final UidGenerator uidGenerator;

	// --- MESSAGE-FILE CACHE ---

	protected final ConcurrentHashMap<String, CachedMessages> messageCache = new ConcurrentHashMap<>();

	// --- CONSTRUCTOR ---

	public ActionInvoker(String actionName, String pathPattern, String pathPrefix, int[] indexes, String[] names,
			Options opts, ServiceInvoker serviceInvoker, AbstractTemplateEngine templateEngine, Route route,
			CallProcessor beforeCall, CallProcessor afterCall, ExecutorService executor, Eventbus eventbus) {
		this.actionName = actionName;
		this.pathPattern = pathPattern;
		this.pathPrefix = pathPrefix;
		this.indexes = indexes;
		this.names = names;
		this.opts = opts;
		this.serviceInvoker = serviceInvoker;
		this.templateEngine = templateEngine;
		this.jsonSerializer = TreeWriterRegistry.getWriter(null);
		this.route = route;
		this.beforeCall = beforeCall;
		this.afterCall = afterCall;
		this.executor = executor;
		this.messageLoader = templateEngine == null ? null : templateEngine.getMessageLoader();
		this.eventbus = eventbus;

		ServiceBrokerConfig cfg = eventbus.getBroker().getConfig();
		this.uidGenerator = cfg.getUidGenerator();
		this.nodeID = cfg.getNodeID();
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
		final Tree params = new Tree();
		if (indexes.length > 0) {

			// Parameters in URL (eg "/path/:id/:name")
			String[] tokens = req.getPath().split("/");
			for (int i = 0; i < indexes.length; i++) {
				params.put(names[i], tokens[indexes[i]]);
			}
		}

		// HTTP GET QueryString
		String query = req.getQuery();
		if (query != null && !query.isEmpty()) {
			parseQueryString(params, query);
		}

		// Multipart or chunked request
		int contentLength = req.getContentLength();
		boolean unknownType = false;
		if (contentLength < 0) {
			String contentType = req.getHeader("Content-Type");
			unknownType = contentType == null || !contentType.contains("/json");
		}
		if (req.isMultipart() || unknownType) {
			executor.execute(() -> {

				// Clear "meta" block to avoid a vulnerability
				Tree meta = params.getMeta(false);
				if (meta != null) {
					meta.clear();
				}
				
				// Custom "before call" processor
				// (eg. copy HTTP headers into the "params" variable)
				if (beforeCall != null) {
					beforeCall.onCall(route, req, rsp, params);
				}
				
				// Invoke service
				serviceInvoker.call(new Context(serviceInvoker, eventbus, uidGenerator, uidGenerator.nextUID(),
						actionName, params, 1, null, null, req.getBody(), opts, nodeID)).then(out -> {
							sendResponse(req, rsp, out);
						}).catchError(cause -> {
							sendError(rsp, cause);
						});
			});
			return;

		}

		// GET without body
		if (contentLength == 0) {
			executor.execute(() -> {

				// Clear "meta" block to avoid a vulnerability
				Tree meta = params.getMeta(false);
				if (meta != null) {
					meta.clear();
				}

				// Custom "before call" processor
				// (eg. copy HTTP headers into the "params" variable)
				if (beforeCall != null) {
					beforeCall.onCall(route, req, rsp, params);
				}

				// Invoke service
				serviceInvoker.call(new Context(serviceInvoker, eventbus, uidGenerator, uidGenerator.nextUID(),
						actionName, params, 1, null, null, null, opts, nodeID)).then(out -> {
							sendResponse(req, rsp, out);
						}).catchError(cause -> {
							logger.error("Unable to invoke action!", cause);
							sendError(rsp, cause);
						});
			});
			return;
		}

		// POST with JSON / QueryString body
		byte[] body = contentLength > 0 ? new byte[contentLength] : null;
		ByteArrayOutputStream buffer = contentLength > 0 ? null : new ByteArrayOutputStream(1024);
		AtomicInteger pos = new AtomicInteger();
		AtomicBoolean faulty = new AtomicBoolean();

		req.getBody().onPacket((bytes, cause, close) -> {
			if (bytes != null && bytes.length > 0) {
				if (contentLength > 0) {
					System.arraycopy(bytes, 0, body, pos.getAndAdd(bytes.length), bytes.length);
				} else {
					buffer.write(bytes);
				}
			} else if (cause != null) {
				faulty.set(true);
				logger.error("Unexpected error occured while receiving and parsing client request!", cause);
				sendError(rsp, cause);
			}
			if (close && !faulty.get()) {

				// Parse and merge body
				Tree merged = parsePostBody(params, body == null ? buffer.toByteArray() : body, req.getContentType());

				// Forward to Thread Pool
				executor.execute(() -> {

					// Clear "meta" block to avoid a vulnerability
					Tree meta = merged.getMeta(false);
					if (meta != null) {
						meta.clear();
					}

					// Custom "before call" processor
					// (eg. copy HTTP headers into the "params" variable)
					if (beforeCall != null) {
						beforeCall.onCall(route, req, rsp, merged);
					}

					// Invoke service
					serviceInvoker.call(new Context(serviceInvoker, eventbus, uidGenerator, uidGenerator.nextUID(),
							actionName, merged, 1, null, null, null, opts, nodeID)).then(out -> {
								sendResponse(req, rsp, out);
							}).catchError(err -> {
								logger.error("Unable to invoke action!", err);
								sendError(rsp, err);
							});
				});
			}
		});
	}

	// --- PARSE BODY OF THE GET / POST REQUEST ---

	protected Tree parsePostBody(Tree params, byte[] bytes, String contentType) throws Exception {
		if (bytes.length > 0) {

			// JSON body?
			if (bytes[0] == '{' || bytes[0] == '[') {
				try {
					Tree json = new Tree(bytes);
					if (params != null && !params.isEmpty()) {
						json.copyFrom(params);
					}
					return json;
				} catch (Exception cause) {
					if (contentType.contains("json")) {
						throw cause;
					}
				}
			}

			// QueryString body?
			String txt = new String(bytes, StandardCharsets.UTF_8);
			if (contentType == null || contentType.contains("x-www")) {
				parseQueryString(params, txt);
			} else {
				parseTextPlain(params, txt);
			}
		}
		return params;
	}

	// --- PARSE TEXT/PLAIN ENCODED REQUEST ---

	protected void parseTextPlain(Tree params, String query) throws UnsupportedEncodingException {
		StringTokenizer st = new StringTokenizer(query, "\r\n");
		String pair;
		int i;
		while (st.hasMoreTokens()) {
			pair = st.nextToken();
			i = pair.indexOf("=");
			if (i > -1) {
				params.put(pair.substring(0, i).trim(), pair.substring(i + 1).trim());
			}
		}
	}

	// --- PARSE HTTP QUERY STRING ---

	protected void parseQueryString(Tree params, String query) throws UnsupportedEncodingException {
		String[] pairs = query.split("&");
		int i;
		for (String pair : pairs) {
			i = pair.indexOf("=");
			if (i > -1) {
				params.put(URLDecoder.decode(pair.substring(0, i), "UTF-8").trim(),
						URLDecoder.decode(pair.substring(i + 1), "UTF-8").trim());
			}
		}
	}

	// --- SEND JSON/STREAMED RESPONSE ---

	protected void sendResponse(WebRequest req, WebResponse rsp, Tree data) throws Exception {

		// Invoke custom "after call" processor
		// (eg. insert custom HTTP headers by data)
		if (afterCall != null) {
			afterCall.onCall(route, req, rsp, data);
		}

		// Disable cache
		rsp.setHeader(CACHE_CONTROL, NO_CACHE);
		if (data == null) {
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
		Tree meta = data.getMeta(false);
		if (meta != null) {

			// Path of the HTML-template
			if (templateEngine != null) {
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
		Object object = data.asObject();
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
				if (templatePath != null && !templatePath.isEmpty()) {

					// Content-type is HTML
					if (!contentTypeSet) {
						rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_HTML);
					}

					// Insert language variables by locale
					if (messageLoader != null) {
						data = insertMessages(data, meta);
					}

					// Invoke template engine
					body = templateEngine.transform(templatePath, data);

				} else {

					// Content-type is JSON
					if (!contentTypeSet) {
						rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_JSON);
					}

					// Serialize
					body = jsonSerializer.toBinary(data.asObject(), null, false);
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

	// --- INSERT MULTILANGUAGE BLOCK INTO DATA ---

	@SuppressWarnings("unchecked")
	protected Tree insertMessages(Tree data, Tree meta) {
		String locale = meta.get(META_LOCALE, "");
		long lastModified = messageLoader.getLastModified(locale);
		CachedMessages cachedMessages = messageCache.get(locale);
		if (cachedMessages == null || cachedMessages.lastModified != lastModified) {
			Tree messages = messageLoader.loadMessages(locale);
			Map<String, Object> messageMap = messages == null ? null : (Map<String, Object>) messages.asObject();
			cachedMessages = new CachedMessages(messageMap, lastModified);
			messageCache.put(locale, cachedMessages);
		}
		if (cachedMessages.messageMap == null) {
			return data;
		}
		return new CheckedTree(new MergedMap((Map<String, Object>) data.asObject(), cachedMessages.messageMap));
	}

	// --- PARENT PROCESSOR ---

	@Override
	public RequestProcessor getParent() {
		return null;
	}

}