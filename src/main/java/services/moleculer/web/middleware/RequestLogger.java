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
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
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
 * reduces the performance (nevertheless, it may be useful during development).
 * Be sure to turn it off in production mode.
 */
@Name("Request Logger")
public class RequestLogger extends HttpMiddleware implements HttpConstants {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(RequestLogger.class);

	// --- PROPERTIES ---

	protected int maxPrintedBytes = 512;
	protected ExecutorService executor = ForkJoinPool.commonPool();

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

				// Create dumper
				RequestDumper dumper = new RequestDumper(req, maxPrintedBytes, executor);

				// Invoke next handler / action
				final long start = System.nanoTime();
				next.service(req, new WebResponse() {

					AtomicBoolean finished = new AtomicBoolean();

					@Override
					public final void setStatus(int code) {
						rsp.setStatus(code);
						dumper.code = code;
					}

					@Override
					public final int getStatus() {
						return rsp.getStatus();
					}

					@Override
					public final void setHeader(String name, String value) {
						rsp.setHeader(name, value);
						dumper.responseHeaders.put(name, value);
					}

					@Override
					public final String getHeader(String name) {
						return dumper.responseHeaders.get(name);
					}

					@Override
					public final void send(byte[] bytes) throws IOException {
						rsp.send(bytes);
						if (maxPrintedBytes < 1 || dumper.out.size() < maxPrintedBytes) {
							dumper.out.write(bytes);
						}
					}

					@Override
					public final boolean end() {
						if (finished.compareAndSet(false, true)) {
							dumper.duration = System.nanoTime() - start;
							boolean ok = rsp.end();
							executor.execute(dumper);
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

	protected static class RequestDumper implements Runnable {

		protected static final char[] CR_LF = System.getProperty("line.separator", "\r\n").toCharArray();
		protected static final char[] HEX = "0123456789ABCDEF".toCharArray();
		protected static final char[] TWO_SPACES = "  ".toCharArray();
		protected static final char[] COLON_SPACE = ": ".toCharArray();
		protected static final char[] ETC = "...".toCharArray();

		protected final WebRequest req;
		protected final int maxPrintedBytes;
		protected final ExecutorService executor;

		protected int code = 200;
		protected LinkedHashMap<String, String> responseHeaders = new LinkedHashMap<>();
		protected ByteArrayOutputStream out = new ByteArrayOutputStream(512);
		protected long duration;

		protected RequestDumper(WebRequest req, int maxPrintedBytes, ExecutorService executor) {
			this.req = req;
			this.maxPrintedBytes = maxPrintedBytes;
			this.executor = executor;
		}

		@Override
		public void run() {
			StringBuilder tmp = new StringBuilder(1024);

			// General request properties
			tmp.append(CR_LF);
			tmp.append(CR_LF);
			tmp.append(TWO_SPACES);
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
			Iterator<String> requestHeaders = req.getHeaders();
			if (requestHeaders != null) {
				String header;
				while (requestHeaders.hasNext()) {
					header = requestHeaders.next();
					tmp.append(TWO_SPACES);
					tmp.append(header);
					tmp.append(COLON_SPACE);
					tmp.append(req.getHeader(header));
					tmp.append(CR_LF);
				}
			}

			// Try to dump request body
			if (req.getContentLength() > 0) {
				PacketStream stream = req.getBody();
				if (stream != null) {
					ByteArrayOutputStream buffer = new ByteArrayOutputStream(512);
					try {
						stream.transferTo(buffer).waitFor(3000);
						printBytes(tmp, buffer.toByteArray(), req.getHeader(CONTENT_TYPE),
								req.getHeader(CONTENT_ENCODING));
					} catch (Exception timeout) {
						tmp.append(CR_LF);
						tmp.append("<read timeouted>");
					}
				}
			}
			tmp.append(CR_LF);

			// It's not known
			tmp.append("  HTTP/1.1 ");

			// Status code and message
			tmp.append(HttpResponseStatus.valueOf(code));
			tmp.append(CR_LF);
			for (Map.Entry<String, String> entry : responseHeaders.entrySet()) {
				tmp.append(TWO_SPACES);
				tmp.append(entry.getKey());
				tmp.append(COLON_SPACE);
				tmp.append(entry.getValue());
				tmp.append(CR_LF);
			}

			// Try to dump body
			if (out.size() > 0) {
				printBytes(tmp, out.toByteArray(), responseHeaders.get(CONTENT_TYPE),
						responseHeaders.get(CONTENT_ENCODING));
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
		}

		protected void printBytes(StringBuilder tmp, byte[] bytes, String contentType, String contentEncoding) {
			tmp.append(CR_LF);
			boolean text = contentEncoding == null && contentType != null
					&& contentType.toLowerCase().contains("utf-8");
			if (text) {
				try {
					String msg = new String(bytes, StandardCharsets.UTF_8).trim();
					if (maxPrintedBytes > 0 && msg.length() > maxPrintedBytes) {
						tmp.append(msg.substring(0, maxPrintedBytes).trim());
						tmp.append(CR_LF);
						tmp.append(ETC);
					} else {
						tmp.append(msg);
					}
					tmp.append(CR_LF);
					return;
				} catch (Exception ignored) {
				}
			}
			printHex(tmp, bytes);
		}

		protected void printHex(StringBuilder tmp, byte[] bytes) {
			char[] printable = new char[16];
			int count = 0;
			printDecimal(tmp, 0);
			for (int j = 0; j < bytes.length; j++) {
				int v = bytes[j] & 0xFF;
				tmp.append(HEX[v >>> 4]);
				tmp.append(HEX[v & 0x0F]);
				tmp.append(' ');
				int pos = count % 16;
				if (v >= 32 && v <= 126) {
					printable[pos] = (char) v;
				} else {
					printable[pos] = '.';
				}
				if (pos == 15) {
					tmp.append(printable);
					tmp.append(CR_LF);
					if (j >= maxPrintedBytes) {
						tmp.append(ETC);
						tmp.append(CR_LF);
						return;
					}
					printDecimal(tmp, j + 1);
				}
				count++;
			}
			tmp.append(CR_LF);
		}

		protected void printDecimal(StringBuilder tmp, int value) {
			String txt = Integer.toHexString(value);
			int len = txt.length();
			for (int i = 0; i < 10 - len; i++) {
				tmp.append('0');
			}
			tmp.append(txt);
			tmp.append(COLON_SPACE);
		}

	}

	// --- PROPERTY GETTERS AND SETTERS ---

	/**
	 * Returns the max number of printed bytes.
	 * 
	 * @return the max number of printed bytes
	 */
	public int getMaxPrintedBytes() {
		return maxPrintedBytes;
	}

	/**
	 * Sets the max number of printed bytes.
	 * 
	 * @param maxDumpSize
	 *            max number of printed bytes
	 */
	public void setMaxPrintedBytes(int maxDumpSize) {
		this.maxPrintedBytes = maxDumpSize;
	}

	/**
	 * Returns the ExecutorService.
	 * 
	 * @return the executor
	 */
	public ExecutorService getExecutor() {
		return executor;
	}

	/**
	 * Sets the ExecutorService.
	 * 
	 * @param executor
	 *            the executor to set
	 */
	public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}

}