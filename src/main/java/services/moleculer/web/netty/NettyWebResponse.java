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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import services.moleculer.web.WebResponse;

public class NettyWebResponse implements WebResponse {

	// --- REQUEST PROPERTIES ----
	
	protected final ChannelHandlerContext ctx;
	protected final NettyWebRequest req;
	
	// --- RESPONSE VARIABLES ---
	
	protected int code = 200;
	protected HashMap<String, String> headers;
	protected AtomicBoolean first = new AtomicBoolean(true);

	// --- CONSTRUCTOR ---
	
	public NettyWebResponse(ChannelHandlerContext ctx, NettyWebRequest req) {
		this.ctx = ctx;
		this.req = req;
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
	 * Writes b.length bytes of body from the specified byte array to the output
	 * stream.
	 * 
	 * @param bytes
	 *            the data
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	public void send(byte[] bytes) {
		if (bytes != null && bytes.length > 0) {
			sendHeaders();
			ctx.write(Unpooled.wrappedBuffer(bytes));
			ctx.flush();
		}
	}

	/**
	 * Completes the asynchronous operation that was started on the request.
	 */
	@Override
	public void end() {
		sendHeaders();
		if (req.parser != null) {
			try {
				req.parser.close();				
			} catch (Exception ignored) {
			}
			req.parser = null;
		}
	}
	
	protected void sendHeaders() {
		if (first.compareAndSet(true, false)) {
			StringBuilder header = new StringBuilder();
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
			header.append("\r\n");
			ctx.write(Unpooled.wrappedBuffer(header.toString().getBytes(StandardCharsets.UTF_8)));
		}	
	}
	
}