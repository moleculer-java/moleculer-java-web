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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.datatree.Tree;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;

@Name("Response Headers")
public class ResponseHeaders extends HttpMiddleware {

	// --- PROPERTIES ---

	protected String[] names;
	protected String[] values;

	// --- CONSTRUCTORS ---

	public ResponseHeaders() {
	}

	public ResponseHeaders(String headerName, String headerValue) {
		set(headerName, headerValue);
	}

	public ResponseHeaders(Map<String, String> headers) {
		setHeaders(headers);
	}

	// --- CREATE NEW PROCESSOR ---

	@Override
	public RequestProcessor install(RequestProcessor next, Tree config) {
		return new RequestProcessor() {

			@Override
			public void service(WebRequest req, WebResponse rsp) throws Exception {

				// Set headers
				if (names != null) {
					int max = names.length;
					if (max == 1) {
						rsp.setHeader(names[0], values[0]);
					} else {
						for (int i = 0; i < max; i++) {
							rsp.setHeader(names[i], values[i]);
						}
					}
				}

				// Invoke next handler (eg. Moleculer Action)
				next.service(req, rsp);
			}

		};
	}

	// --- ADD / REMOVE HEADER ---

	public ResponseHeaders set(String headerName, String headerValue) {
		LinkedHashMap<String, String> headers = new LinkedHashMap<>();
		headers.putAll(getHeaders());
		headers.put(headerName, headerValue);
		setHeaders(headers);
		return this;
	}

	public ResponseHeaders remove(String headerName) {
		LinkedHashMap<String, String> headers = new LinkedHashMap<>();
		headers.putAll(getHeaders());
		headers.remove(headerName);
		setHeaders(headers);
		return this;
	}

	// --- MODIFY HEADERS ---

	public Map<String, String> getHeaders() {
		LinkedHashMap<String, String> headers = new LinkedHashMap<>();
		if (names != null) {
			for (int i = 0; i < names.length; i++) {
				headers.put(names[i], values[i]);
			}
		}

		// Use "setHeaders" to modify the headers "Map"
		return Collections.unmodifiableMap(headers);
	}

	public void setHeaders(Map<String, String> headers) {
		final String[] n = names;
		final String[] v = values;
		try {
			if (headers == null || headers.isEmpty()) {
				names = null;
				values = null;
				return;
			}
			int size = headers.size();
			names = new String[size];
			values = new String[size];
			int pos = 0;
			String t;
			for (Map.Entry<String, String> entry : headers.entrySet()) {
				t = Objects.requireNonNull(entry.getKey(), "Header name is null!").trim();
				if (t.isEmpty()) {
					throw new IllegalArgumentException("Empty header name!");
				}
				names[pos] = t;
				t = Objects.requireNonNull(entry.getValue(), "Header value is null!").trim();
				if (t.isEmpty()) {
					throw new IllegalArgumentException("Empty header value!");
				}
				values[pos] = t;
			}
		} catch (Exception cause) {
			names = n;
			values = v;
			throw cause;
		}
	}

}
