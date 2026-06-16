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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import services.moleculer.ServiceBroker;
import services.moleculer.monitor.ConstantMonitor;
import services.moleculer.web.netty.NettyServer;

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

	/**
	 * Reads exactly one HTTP/1.1 response from a kept-alive stream: the header
	 * block up to the CRLFCRLF terminator, then Content-Length body bytes (if
	 * any). Returns the full response as text, or whatever was read before the
	 * peer closed the connection (so a reset surfaces as a failed assertion).
	 */
	private static String readHttpResponse(InputStream is) throws Exception {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int b;

		// Read until end of header block ("\r\n\r\n").
		while ((b = is.read()) != -1) {
			buffer.write(b);
			byte[] a = buffer.toByteArray();
			int n = a.length;
			if (n >= 4 && a[n - 4] == '\r' && a[n - 3] == '\n' && a[n - 2] == '\r' && a[n - 1] == '\n') {
				break;
			}
		}

		String headers = buffer.toString(StandardCharsets.UTF_8);
		int contentLength = 0;
		for (String line : headers.split("\r\n")) {
			int colon = line.indexOf(':');
			if (colon > 0 && "Content-Length".equalsIgnoreCase(line.substring(0, colon).trim())) {
				contentLength = Integer.parseInt(line.substring(colon + 1).trim());
				break;
			}
		}

		// Read the body (if framed by Content-Length).
		for (int i = 0; i < contentLength; i++) {
			b = is.read();
			if (b == -1) {
				break;
			}
			buffer.write(b);
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

}
