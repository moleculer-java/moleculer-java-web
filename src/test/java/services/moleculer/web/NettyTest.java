/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2019 Andras Berkes [andras.berkes@programmer.net]<br>
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
package services.moleculer.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.junit.jupiter.api.Test;

import services.moleculer.ServiceBroker;
import services.moleculer.monitor.ConstantMonitor;
import services.moleculer.web.netty.NettyServer;
import services.moleculer.web.router.Route;

public class NettyTest extends AbstractTemplateTest {

	protected NettyServer server;

	@Override
	protected void startServer() throws Exception {
		br = ServiceBroker.builder().monitor(new ConstantMonitor()).build();

		server = new NettyServer();
		br.createService(server);

		gw = new ApiGateway();
		br.createService(gw);

		br.start();

		System.out.println(br.getConfig().getCacher());
	}

	@Override
	protected void stopServer() throws Exception {

		// The Netty server is registered as a broker service, so it is stopped
		// together with the broker in AbstractTemplateTest#tearDown().
	}

	/**
	 * A body-less response (eg. 204 No Content) must NOT close the kept-alive
	 * TCP socket. Reproduces the ECONNRESET that pooled HTTP/1.1 clients hit:
	 * the "/nocontent" middleware (see AbstractTemplateTest#setUp) emits a 204
	 * with a header but no Content-Length; the same raw socket is then reused
	 * for a second request. With the bug, NettyWebResponse.end() closes the
	 * socket and the second read sees EOF; with the framing guard it stays open.
	 */
	@Test
	public void testKeepAliveAfterNoContent() throws Exception {
		try (Socket socket = new Socket("127.0.0.1", 3000)) {
			socket.setSoTimeout(10000);
			OutputStream os = socket.getOutputStream();
			InputStream is = socket.getInputStream();

			// 1) Request that yields a body-less 204 No Content
			os.write("GET /nocontent HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();
			String first = readHttpResponse(is);
			assertTrue(first.startsWith("HTTP/1.1 204"), "expected 204, got: " + first);
			assertTrue(first.contains("Content-Length: 0"), "framing guard did not run: " + first);

			// Give an (erroneous) async close time to land so the failure below
			// is deterministic when the bug is present.
			Thread.sleep(200);

			// 2) Reuse the SAME connection for a second request
			os.write("GET /math/add/3/4 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();
			String second = readHttpResponse(is);
			assertTrue(second.startsWith("HTTP/1.1 200"), "kept-alive socket was reset; got: " + second);
		}
	}

	// --- RESPONSE FRAMING (length-less responses) ---

	/**
	 * A streamed Action result (a PacketStream, so no Content-Length can be
	 * set) must be sent with "Transfer-Encoding: chunked" to an HTTP/1.1
	 * client, and the kept-alive socket must stay usable afterwards. Before
	 * the fix the body went out unframed and the server closed the socket.
	 */
	@Test
	public void testStreamedResponseIsChunkedAndKeepsAlive() throws Exception {
		try (Socket socket = new Socket("127.0.0.1", 3000)) {
			socket.setSoTimeout(10000);
			OutputStream os = socket.getOutputStream();
			InputStream is = socket.getInputStream();

			os.write("GET /chunked/download HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();
			RawResponse first = readRawResponse(is);
			assertTrue(first.headers.startsWith("HTTP/1.1 200"), first.headers);
			assertTrue(first.hasHeader("Transfer-Encoding", "chunked"), first.headers);
			assertFalse(first.hasHeader("Content-Length", null), first.headers);
			assertFalse(first.hasHeader("Connection", "close"), first.headers);
			assertArrayEquals(ChunkedService.downloadPattern(), first.body);

			// Reuse the SAME connection for a second request
			os.write("GET /math/add/3/4 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();
			RawResponse second = readRawResponse(is);
			assertTrue(second.headers.startsWith("HTTP/1.1 200"), "kept-alive socket was reset; got: " + second.headers);
			assertTrue(new String(second.body, StandardCharsets.UTF_8).contains("7"), second.headers);
		}
	}

	/**
	 * An HTTP/1.0 client cannot decode chunked bodies: a length-less response
	 * is close-delimited, and the server must say so ("Connection: close").
	 */
	@Test
	public void testStreamedResponseHttp10IsCloseDelimited() throws Exception {
		try (Socket socket = new Socket("127.0.0.1", 3000)) {
			socket.setSoTimeout(10000);
			OutputStream os = socket.getOutputStream();
			InputStream is = socket.getInputStream();

			os.write("GET /chunked/download HTTP/1.0\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();
			RawResponse rsp = splitRaw(readToEof(is));
			assertTrue(rsp.headers.startsWith("HTTP/1.1 200"), rsp.headers);
			assertTrue(rsp.hasHeader("Connection", "close"), rsp.headers);
			assertFalse(rsp.hasHeader("Transfer-Encoding", null), rsp.headers);
			assertFalse(rsp.hasHeader("Content-Length", null), rsp.headers);
			assertArrayEquals(ChunkedService.downloadPattern(), rsp.body);
		}
	}

	/**
	 * "Connection: close" requested by an HTTP/1.1 client: the server must
	 * advertise the close in the response and then close the socket.
	 */
	@Test
	public void testConnectionCloseRequestIsHonoured() throws Exception {
		try (Socket socket = new Socket("127.0.0.1", 3000)) {
			socket.setSoTimeout(10000);
			OutputStream os = socket.getOutputStream();
			InputStream is = socket.getInputStream();

			os.write("GET /math/add/3/4 HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n"
					.getBytes(StandardCharsets.UTF_8));
			os.flush();
			RawResponse rsp = splitRaw(readToEof(is));
			assertTrue(rsp.headers.startsWith("HTTP/1.1 200"), rsp.headers);
			assertTrue(rsp.hasHeader("Connection", "close"), rsp.headers);
			assertTrue(rsp.hasHeader("Content-Length", null), rsp.headers);
			assertTrue(new String(rsp.body, StandardCharsets.UTF_8).contains("7"), rsp.headers);
		}
	}

	/**
	 * A body written without ANY header (no Content-Length, no Content-Type)
	 * must still be framed. Before the fix end() hit a NullPointerException on
	 * the empty header map, swallowed it, and left the socket neither framed
	 * nor closed - the client hung until its read timeout.
	 */
	@Test
	public void testBodyWithoutHeadersIsFramedAndKeepsAlive() throws Exception {
		try (Socket socket = new Socket("127.0.0.1", 3000)) {
			socket.setSoTimeout(10000);
			OutputStream os = socket.getOutputStream();
			InputStream is = socket.getInputStream();

			os.write("GET /rawbody HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();
			RawResponse first = readRawResponse(is);
			assertTrue(first.headers.startsWith("HTTP/1.1 200"), first.headers);
			assertTrue(first.hasHeader("Transfer-Encoding", "chunked"), first.headers);
			assertEquals("raw-body", new String(first.body, StandardCharsets.UTF_8));

			// Reuse the SAME connection for a second request
			os.write("GET /math/add/3/4 HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();
			RawResponse second = readRawResponse(is);
			assertTrue(second.headers.startsWith("HTTP/1.1 200"), "kept-alive socket was reset; got: " + second.headers);
		}
	}

	/**
	 * An I/O error in the middle of a streamed response, after the headers and
	 * the first packet are on the wire: the gateway must abort the connection
	 * WITHOUT the chunked terminator, so the client can tell the body is
	 * truncated. Before the fix the default JSON error was appended to the
	 * partial download under a "successful" 200, and the terminator followed.
	 */
	@Test
	public void testMidStreamErrorIsVisibleAsTruncation() throws Exception {
		try (Socket socket = new Socket("127.0.0.1", 3000)) {
			socket.setSoTimeout(10000);
			OutputStream os = socket.getOutputStream();
			InputStream is = socket.getInputStream();

			os.write("GET /chunked/fail HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();

			// The server must close the socket (readToEof would time out otherwise)
			String raw = new String(readToEof(is), StandardCharsets.ISO_8859_1);
			int headerEnd = raw.indexOf("\r\n\r\n");
			assertTrue(headerEnd > 0, raw);
			RawResponse rsp = new RawResponse(raw.substring(0, headerEnd), new byte[0]);
			String body = raw.substring(headerEnd + 4);
			assertTrue(rsp.headers.startsWith("HTTP/1.1 200"), rsp.headers);
			assertTrue(rsp.hasHeader("Transfer-Encoding", "chunked"), rsp.headers);

			// First packet (1000 bytes = 0x3e8) arrived as a chunk...
			assertTrue(body.startsWith("3e8\r\n"), "first chunk missing: " + body);

			// ...but no terminator and no error JSON after it
			assertFalse(body.endsWith("0\r\n\r\n"), "truncated body must not carry the chunked terminator");
			assertFalse(body.contains("\"message\""), "error JSON must not be appended to a partial body: " + body);
		}
	}

	/**
	 * A middleware that fails synchronously (before the request is routed to an
	 * Action) is a connector-level error: it must reach the gateway-level
	 * "onError" handler, with a null Route.
	 */
	@Test
	public void testConnectorErrorReachesGatewayOnError() throws Exception {
		AtomicReference<Route> seenRoute = new AtomicReference<>(new Route());
		AtomicReference<String> seenPath = new AtomicReference<>();
		gw.setOnError((route, req, rsp, cause) -> {
			seenRoute.set(route);
			seenPath.set(req.getPath());
			sendCustomError(rsp, 502, "connector:" + cause.getMessage());
		});
		SimpleHttpResponse rsp = fetch("/throw");
		assertEquals(502, rsp.getCode());
		assertEquals("connector:Simulated middleware failure", body(rsp));
		assertNull(seenRoute.get(), "connector-level errors have no Route");
		assertEquals("/throw", seenPath.get());
	}

	// --- RAW HTTP HELPERS ---

	/**
	 * One raw HTTP response: the header block (status line + headers, without
	 * the terminating CRLFCRLF) and the decoded body.
	 */
	protected static final class RawResponse {

		final String headers;
		final byte[] body;

		RawResponse(String headers, byte[] body) {
			this.headers = headers;
			this.body = body;
		}

		/**
		 * Case-insensitive header check; value == null means "present".
		 */
		boolean hasHeader(String name, String value) {
			String v = headerValue(headers, name);
			if (v == null) {
				return false;
			}
			return value == null || v.trim().equalsIgnoreCase(value);
		}

	}

	/**
	 * Reads exactly one HTTP/1.1 response from a kept-alive stream (header
	 * block, then the body delimited by Content-Length or chunked transfer
	 * coding; a length-less response is read to EOF) and returns it as text -
	 * or whatever was read before the peer closed the connection, so a reset
	 * surfaces as a failed assertion.
	 */
	protected static String readHttpResponse(InputStream is) throws Exception {
		RawResponse rsp = readRawResponse(is);
		return rsp.headers + "\r\n\r\n" + new String(rsp.body, StandardCharsets.UTF_8);
	}

	/**
	 * Reads exactly one HTTP response from a (possibly kept-alive) stream and
	 * decodes its body: Content-Length, chunked, or - for a response without
	 * either - read to EOF (close-delimited). 204/304 have no body.
	 */
	protected static RawResponse readRawResponse(InputStream is) throws IOException {
		String headers = readHeaderBlock(is);
		int status = Integer.parseInt(headers.substring(9, 12));
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		String contentLength = headerValue(headers, "Content-Length");
		String transferEncoding = headerValue(headers, "Transfer-Encoding");
		if (status == 204 || status == 304) {

			// No body
		} else if (contentLength != null) {
			int len = Integer.parseInt(contentLength.trim());
			for (int i = 0; i < len; i++) {
				int b = is.read();
				if (b == -1) {
					break;
				}
				body.write(b);
			}
		} else if (transferEncoding != null && transferEncoding.toLowerCase().contains("chunked")) {
			while (true) {
				String sizeLine = readLine(is);
				int semicolon = sizeLine.indexOf(';');
				int size = Integer.parseInt((semicolon > -1 ? sizeLine.substring(0, semicolon) : sizeLine).trim(), 16);
				if (size == 0) {

					// Trailer section, up to the empty line
					while (!readLine(is).isEmpty()) {
					}
					break;
				}
				for (int i = 0; i < size; i++) {
					int b = is.read();
					if (b == -1) {
						throw new IOException("EOF inside a chunk");
					}
					body.write(b);
				}
				readLine(is);
			}
		} else {
			body.write(readToEof(is));
		}
		return new RawResponse(headers, body.toByteArray());
	}

	/**
	 * Splits a whole (close-delimited) response into header block and body.
	 */
	protected static RawResponse splitRaw(byte[] raw) {
		String text = new String(raw, StandardCharsets.ISO_8859_1);
		int headerEnd = text.indexOf("\r\n\r\n");
		if (headerEnd < 0) {
			return new RawResponse(text, new byte[0]);
		}
		byte[] body = new byte[raw.length - headerEnd - 4];
		System.arraycopy(raw, headerEnd + 4, body, 0, body.length);
		return new RawResponse(text.substring(0, headerEnd), body);
	}

	/**
	 * Reads the header block up to (not including) the CRLFCRLF terminator.
	 */
	protected static String readHeaderBlock(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int b;
		while ((b = is.read()) != -1) {
			buffer.write(b);
			byte[] a = buffer.toByteArray();
			int n = a.length;
			if (n >= 4 && a[n - 4] == '\r' && a[n - 3] == '\n' && a[n - 2] == '\r' && a[n - 1] == '\n') {
				return new String(a, 0, n - 4, StandardCharsets.UTF_8);
			}
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Reads one CRLF-terminated line (without the CRLF).
	 */
	protected static String readLine(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int b;
		while ((b = is.read()) != -1) {
			if (b == '\n') {
				break;
			}
			if (b != '\r') {
				buffer.write(b);
			}
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Reads until the peer closes the connection.
	 */
	protected static byte[] readToEof(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int b;
		while ((b = is.read()) != -1) {
			buffer.write(b);
		}
		return buffer.toByteArray();
	}

	/**
	 * Value of a header in a raw header block (case-insensitive), or null.
	 */
	protected static String headerValue(String headers, String name) {
		for (String line : headers.split("\r\n")) {
			int colon = line.indexOf(':');
			if (colon > 0 && name.equalsIgnoreCase(line.substring(0, colon).trim())) {
				return line.substring(colon + 1);
			}
		}
		return null;
	}

}
