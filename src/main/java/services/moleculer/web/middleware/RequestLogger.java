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

import static services.moleculer.util.CommonUtils.formatNamoSec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import io.netty.handler.codec.http.HttpResponseStatus;
import services.moleculer.service.Name;
import services.moleculer.stream.PacketStream;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * Writes request and response into the log. WARNING: Using this middleware
 * reduces the performance (however, it may be useful during development).
 */
@Name("Request Logger")
public class RequestLogger extends HttpMiddleware implements HttpConstants {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(RequestLogger.class);

	// --- NEW LINE ---

	protected static final char[] CR_LF = System.getProperty("line.separator", "\r\n").toCharArray();

	// --- PROPERTIES ---

	protected int maxPrintedBytes = 512;

	// --- CONSTRUCTORS ---

	public RequestLogger() {
	}

	public RequestLogger(int maxPrintedBytes) {
		setMaxPrintedBytes(maxPrintedBytes);
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
				StringBuilder tmp = new StringBuilder(1024);

				// General request properties
				tmp.append(CR_LF);
				tmp.append(CR_LF);
				tmp.append("  ");
				tmp.append(req.getMethod());
				tmp.append(' ');
				tmp.append(req.getPath());
				String query = req.getQuery();
				if (query != null && !query.isEmpty()) {
					tmp.append('?');
					tmp.append(query);
				}

				// It's not known (but Servlet and Netty use HTTP1.1)
				tmp.append(" HTTP/1.1");

				tmp.append(CR_LF);
				Iterator<String> headers = req.getHeaders();
				if (headers != null) {
					String header;
					while (headers.hasNext()) {
						header = headers.next();
						tmp.append("  ");
						tmp.append(header);
						tmp.append(": ");
						tmp.append(req.getHeader(header));
						tmp.append(CR_LF);
					}
				}

				// Try to dump body
				if (req.isMultipart()) {
					tmp.append(CR_LF);
					tmp.append("  <streamed body>");
				} else if (req.getContentLength() > 0) {
					PacketStream stream = req.getBody();
					if (stream == null) {
						tmp.append(CR_LF);
						tmp.append("  <missing body>");
					} else {
						ByteArrayOutputStream buffer = new ByteArrayOutputStream(512);
						try {
							stream.transferTo(buffer).waitFor(3000);
							printBytes(tmp, buffer.toByteArray());
						} catch (Exception timeout) {
							tmp.append(CR_LF);
							tmp.append("  <read timeouted>");
						}
					}
				}
				tmp.append(CR_LF);

				// Invoke next handler / action
				final long start = System.nanoTime();
				next.service(req, new WebResponse() {

					int code = 200;
					LinkedHashMap<String, String> headers = new LinkedHashMap<>();
					ByteArrayOutputStream out = new ByteArrayOutputStream(512);
					AtomicBoolean finished = new AtomicBoolean();

					@Override
					public final void setStatus(int code) {
						rsp.setStatus(code);
						this.code = code;
					}

					@Override
					public final int getStatus() {
						return rsp.getStatus();
					}

					@Override
					public final void setHeader(String name, String value) {
						rsp.setHeader(name, value);
						headers.put(name, value);
					}

					@Override
					public final String getHeader(String name) {
						return headers.get(name);
					}

					@Override
					public final void send(byte[] bytes) throws IOException {
						rsp.send(bytes);
						if (out.size() < maxPrintedBytes) {
							out.write(bytes);
						}
					}

					@Override
					public final boolean end() {
						if (finished.compareAndSet(false, true)) {
							long duration = System.nanoTime() - start;
							boolean ok = rsp.end();

							// It's not known (but Servlet and Netty use
							// HTTP1.1)
							tmp.append("  HTTP/1.1 ");

							// Status code and message
							tmp.append(HttpResponseStatus.valueOf(code));
							tmp.append(CR_LF);
							for (Map.Entry<String, String> entry : headers.entrySet()) {
								tmp.append("  ");
								tmp.append(entry.getKey());
								tmp.append(": ");
								tmp.append(entry.getValue());
								tmp.append(CR_LF);
							}

							// Try to dump body
							if (out.size() > 0) {
								printBytes(tmp, out.toByteArray());
							}

							// Insert processing time (first line)
							tmp.insert(0, '.');
							tmp.insert(0, formatNamoSec(duration));
							tmp.insert(0, " processed within ");

							// Client address
							String address = req.getAddress();
							if (address == null || address.isEmpty()) {
								tmp.insert(0, "<unknown host>");
							} else {
								tmp.insert(0, address);
							}
							tmp.insert(0, "Request from ");

							// Write to log
							logger.info(tmp.toString());
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

	protected void printBytes(StringBuilder tmp, byte[] bytes) {
		if (bytes == null || bytes.length == 0 || maxPrintedBytes < 1) {
			return;
		}
		tmp.append(CR_LF);
		// tmp.append(" ");
		byte b;
		char c;
		int max = Math.min(bytes.length, maxPrintedBytes);
		for (int i = 0; i < max; i++) {
			b = bytes[i];
			c = (char) b;
			if (!Character.isISOControl(c) || c == '\r' || c == '\n' || c == '\t') {
				tmp.append(c);
			} else {
				tmp.append('[');
				tmp.append(b & 0xFF);
				tmp.append(']');
			}
		}
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	/**
	 * @return the maxPrintedBytes
	 */
	public int getMaxPrintedBytes() {
		return maxPrintedBytes;
	}

	/**
	 * @param maxPrintedBytes
	 *            the maxPrintedBytes to set
	 */
	public void setMaxPrintedBytes(int maxDumpSize) {
		this.maxPrintedBytes = maxDumpSize;
	}

}