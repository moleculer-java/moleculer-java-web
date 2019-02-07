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

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import io.datatree.Tree;
import services.moleculer.context.CallOptions;
import services.moleculer.context.CallOptions.Options;
import services.moleculer.service.ServiceInvoker;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;

public class ActionInvoker implements RequestProcessor {

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
	
	// --- CONSTRUCTOR ---
	
	public ActionInvoker(String actionName, String pathPattern, boolean isStatic, String pathPrefix, int[] indexes,
			String[] names, Options opts, ServiceInvoker serviceInvoker) {
		this.actionName = actionName;
		this.pathPattern = pathPattern;
		this.isStatic = isStatic;
		this.pathPrefix = pathPrefix;
		this.indexes = indexes;
		this.names = names;
		this.opts = opts;
		this.serviceInvoker = serviceInvoker;
	}

	// --- PROCESS (SERVLET OR NETTY) HTTP REQUEST ---

	@Override
	public void service(WebRequest req, WebResponse rsp) throws Exception {
		
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
		
		// Parse body
		int contentLength = req.getContentLength();
		if (contentLength > 0) {
			if (req.isMultipart()) {
				
				// Multipart POST request
				if (params == null) {
					params = new Tree();
				}
				
				// Invoke service
				serviceInvoker.call(actionName, params, opts, req.getBody(), null).then(out -> {
					sendResponse(rsp, out);
				}).catchError(cause -> {
					sendResponse(rsp, cause);
				});

			} else {
				
				// JSON or QueryString POST
				final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				final Tree urlParams = params;
				req.getBody().transferTo(buffer).then(ok -> {
					
					// Parse body
					Tree postParams = parsePostBody(buffer.toByteArray());
					
					// Merge with URL parameters
					if (urlParams != null) {
						postParams.copyFrom(urlParams, true);
					}

					// Invoke service
					serviceInvoker.call(actionName, postParams, opts, null, null).then(out -> {
						sendResponse(rsp, out);
					}).catchError(cause -> {
						sendResponse(rsp, cause);
					});
				}).catchError(cause -> {
					sendResponse(rsp, cause);
				});
			}
		}
	}

	// --- PARSE REQUEST ---
	
	protected Tree parsePostBody(byte[] bytes) throws Exception {
		Tree params;
		if (bytes.length == 0) {
			params = new Tree();
		} else {
			if (bytes[0] == '{' || bytes[0] == '[') {
				
				// JSON body
				params = new Tree(bytes);
			} else {
				
				// QueryString
				params = parseQueryString(new String(bytes, StandardCharsets.UTF_8));
			}
		}
		return params;
	}
	
	// --- PARSE QUERY STRING ---
	
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
	
	// --- SEND JSON/STREAM RESPONSE ---
	
	protected void sendResponse(WebResponse rsp, Tree out) {
		
	}

	// --- SEND ERROR RESPONSE ---
	
	protected void sendResponse(WebResponse rsp, Throwable cause) {
		
	}

}