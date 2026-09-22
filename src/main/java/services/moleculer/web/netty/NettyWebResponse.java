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
package services.moleculer.web.netty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * WebResponse of the Netty connector. The response is written by hand (there
 * is no HttpResponseEncoder in the pipeline): {@link #sendHeaders()} emits the
 * status line and the headers once, {@link #send(byte[])} the body,
 * {@link #end()} finishes the response.
 * <p>
 * <b>Framing</b> (RFC 9112 section 6): a response is delimited by its
 * {@code Content-Length} when the caller set one; otherwise the body is sent
 * with {@code Transfer-Encoding: chunked} (eg. a streamed Action result whose
 * length is unknown), so the kept-alive connection can be reused and a
 * truncated body is detectable. Only for HTTP/1.0 clients (no chunked
 * support) is a length-less response close-delimited, advertised with
 * {@code Connection: close}. A caller that sets a {@code Transfer-Encoding}
 * header itself is responsible for the framing of the bytes it sends.
 */
public class NettyWebResponse implements WebResponse, HttpConstants {

	// --- CONSTANTS ---

	protected static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.US_ASCII);
	protected static final byte[] LAST_CHUNK = "0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(NettyWebResponse.class);

	// --- REQUEST PROPERTIES ----

	protected final ChannelHandlerContext ctx;
	protected final NettyWebRequest req;
	protected final Channel channel;

	/**
	 * HEAD request: the headers are sent, the body is dropped.
	 */
	protected final boolean head;

	/**
	 * Custom properties (for inter-middleware communication).
	 */
	protected HashMap<String, Object> properties;

	// --- RESPONSE VARIABLES ---

	protected int code = 200;
	protected HashMap<String, String> headers;
	protected AtomicBoolean first = new AtomicBoolean(true);
	protected final AtomicBoolean ended = new AtomicBoolean();

	// --- FRAMING (decided once, in sendHeaders) ---

	/**
	 * The body is sent with "Transfer-Encoding: chunked" (no Content-Length).
	 */
	protected volatile boolean chunked;

	/**
	 * The connection is closed after the response ("Connection: close").
	 */
	protected volatile boolean closeOnEnd;

	// --- CONSTRUCTOR ---

	public NettyWebResponse(ChannelHandlerContext ctx, NettyWebRequest req) {
		this.ctx = ctx;
		this.req = req;
		this.channel = ctx.channel();
		this.head = req != null && HEAD.equals(req.getMethod());
	}

	// --- PUBLIC WEBRESPONSE METHODS ---

	/**
	 * Sets the status code for this response. This method is used to set the
	 * return status code when there is no error (for example, for the 200 or
	 * 404 status codes). This method preserves any cookies and other response
	 * headers. Valid status codes are those in the 2XX, 3XX, 4XX, and 5XX
	 * ranges. Other status codes are treated as container specific.
	 * 
	 * @param code
	 *            the status code
	 */
	@Override
	public void setStatus(int code) {
		this.code = code;
	}

	/**
	 * Gets the current status code of this response.
	 * 
	 * @return the status code
	 */
	@Override
	public int getStatus() {
		return code;
	}

	/**
	 * Sets a response header with the given name and value. If the header had
	 * already been set, the new value overwrites the previous one.
	 * 
	 * @param name
	 *            the name of the header
	 * @param value
	 *            the header value If it contains octet string, it should be
	 *            encoded according to RFC 2047
	 */
	@Override
	public void setHeader(String name, String value) {
		if (headers == null) {
			headers = new HashMap<>();
		}
		headers.put(name, value);
	}

	/**
	 * Returns the value of the specified response header as a String. If the
	 * response did not include a header of the specified name, this method
	 * returns null. If there are multiple headers with the same name, this
	 * method returns the first head in the response.
	 * 
	 * @param name
	 *            name a String specifying the header name
	 * 
	 * @return a String containing the value of the response header, or null if
	 *         the response does not have a header of that name
	 */
	@Override
	public String getHeader(String name) {
		if (headers == null) {
			return null;
		}
		return headers.get(name);
	}

	/**
	 * Writes b.length bytes of body from the specified byte array to the output
	 * stream.
	 * 
	 * @param bytes
	 *            the data
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	public void send(byte[] bytes) throws IOException {
		if (bytes != null && bytes.length > 0) {
			if (!channel.isOpen()) {
				throw new IOException("Socket closed!");
			}
			sendHeaders();

			// A HEAD response carries the same headers (including Content-Length)
			// as the equivalent GET, but MUST NOT include a message body
			// (RFC 9110, section 9.3.2). Emit the headers and drop the body so
			// HTTP clients that strictly honor the no-body rule (eg.
			// AsyncHttpClient 3.x) do not leave the kept-alive connection out of
			// sync with leftover body bytes.
			if (head) {
				ctx.flush();
				return;
			}
			if (chunked) {

				// Chunked transfer coding: <hex length> CRLF <bytes> CRLF
				byte[] size = (Integer.toHexString(bytes.length) + "\r\n").getBytes(StandardCharsets.US_ASCII);
				ctx.write(Unpooled.wrappedBuffer(size, bytes, CRLF));
			} else {
				ctx.write(Unpooled.wrappedBuffer(bytes));
			}
			ctx.flush();
		}
	}

	/**
	 * Completes the asynchronous operation that was started on the request:
	 * writes the chunked terminator (if the body was chunked), closes the
	 * connection (if the response is close-delimited or the client asked for
	 * it), and releases the multipart parser. Idempotent - only the first call
	 * does anything.
	 *
	 * @return true on the first call, false afterwards
	 */
	@Override
	public boolean end() {
		if (!ended.compareAndSet(false, true)) {
			return false;
		}

		// Body-less responses (eg. 204 No Content, or an action returning null)
		// that never set a Content-Length: emit an explicit "Content-Length: 0"
		// so the response is framed and the kept-alive connection is reused
		// (pooled HTTP/1.1 clients hit ECONNRESET otherwise). first.get() ==
		// true means send() never wrote a body.
		if (first.get() && (headers == null || !headers.containsKey(CONTENT_LENGTH))) {
			setHeader(CONTENT_LENGTH, "0");
		}
		sendHeaders();
		try {
			if (chunked) {
				ctx.write(Unpooled.wrappedBuffer(LAST_CHUNK));
			}
			if (closeOnEnd) {
				ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
			} else {
				ctx.flush();
			}
		} catch (Exception cause) {
			logger.debug("Unable to finish response!", cause);
		}
		if (req != null && req.parser != null) {
			try {
				req.parser.close();
			} catch (Exception ignored) {
			}
			req.parser = null;
		}
		return true;
	}

	/**
	 * Writes the status line and the headers (once). This is the single point
	 * where the framing of the response is decided, because the Content-Length
	 * (if any) is final here for every caller: send() sets it before the first
	 * write, end() sets "0" for body-less responses.
	 */
	protected void sendHeaders() {
		if (first.compareAndSet(true, false)) {

			// Framing decision (RFC 9112 section 6): Content-Length when set;
			// otherwise chunked - except for HTTP/1.0 clients, which get a
			// close-delimited response. A HEAD response has no body to frame.
			// A caller-supplied Transfer-Encoding means the caller frames the
			// body itself (never double-frame it).
			boolean hasLength = headers != null && headers.containsKey(CONTENT_LENGTH);
			boolean hasEncoding = headers != null && headers.containsKey(TRANSFER_ENCODING);
			boolean http10 = req != null && HttpVersion.HTTP_1_0.equals(req.httpVersion);
			String reqConnection = req == null ? null : req.getHeader(CONNECTION);
			String rspConnection = headers == null ? null : headers.get(CONNECTION);
			if (!hasLength && !hasEncoding && !head) {
				if (http10) {
					closeOnEnd = true;
				} else {
					chunked = true;
				}
			}

			// Connection handling: the client asked for "close", the client is
			// HTTP/1.0 without "keep-alive", or the response itself says "close"
			if (CLOSE.equalsIgnoreCase(reqConnection) || (http10 && !KEEP_ALIVE.equalsIgnoreCase(reqConnection))
					|| CLOSE.equalsIgnoreCase(rspConnection)) {
				closeOnEnd = true;
			}

			// Status line + headers
			StringBuilder header = new StringBuilder(512);
			if (code == 200) {
				header.append("HTTP/1.1 200 Ok\r\n");
			} else {
				header.append("HTTP/1.1 ");
				header.append(HttpResponseStatus.valueOf(code));
				header.append("\r\n");
			}
			if (headers != null) {
				for (Map.Entry<String, String> entry : headers.entrySet()) {
					header.append(entry.getKey());
					header.append(": ");
					header.append(entry.getValue());
					header.append("\r\n");
				}
			}
			if (chunked) {
				header.append(TRANSFER_ENCODING);
				header.append(": ");
				header.append(CHUNKED);
				header.append("\r\n");
			}
			if (closeOnEnd && rspConnection == null) {
				header.append(CONNECTION);
				header.append(": ");
				header.append(CLOSE);
				header.append("\r\n");
			}
			header.append("\r\n");
			ctx.write(Unpooled.wrappedBuffer(header.toString().getBytes(StandardCharsets.UTF_8)));
			ctx.flush();
		}
	}

	// --- CUSTOM PROPERTIES ---

	/**
	 * Associates the specified value with the specified "name" in this
	 * WebResponse. If the WebResponse previously contained a mapping for the
	 * "name", the old value is replaced.
	 * 
	 * @param name
	 *            a "name" with which the specified value is to be associated
	 * @param value
	 *            value to be associated with the specified "name"
	 */
	@Override
	public void setProperty(String name, Object value) {
		if (properties == null) {
			properties = new HashMap<>();
		}
		properties.put(name, value);
	}

	/**
	 * Returns the value to which the specified "name" is mapped, or null if
	 * this WebResponse contains no mapping for the "name".
	 * 
	 * @param name
	 *            the "name" whose associated value is to be returned
	 * 
	 * @return the value to which the specified "name" is mapped, or null if
	 *         this WebResponse contains no mapping for the "name"
	 */
	@Override
	public Object getProperty(String name) {
		if (properties == null) {
			return null;
		}
		return properties.get(name);
	}

	// --- ACCESS TO INTERNAL OBJECT ---
	
	/**
	 * Returns the internal object of this WebResponse.
	 * 
	 * @return internal object (Netty ChannelHandlerContext)
	 */
	@Override
	public Object getInternalObject() {
		return ctx;
	}

}