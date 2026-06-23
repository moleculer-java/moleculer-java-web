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
package services.moleculer.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import services.moleculer.ServiceBroker;
import services.moleculer.monitor.ConstantMonitor;
import services.moleculer.service.Action;
import services.moleculer.service.Service;
import services.moleculer.web.netty.NettyServer;

/**
 * Slowloris / partial-request regression test for the standalone Netty
 * connector. With {@link NettyServer#setReadTimeout(int)} configured, a client
 * that opens a connection but never finishes sending its request must be
 * disconnected by the server (so half-open connections cannot accumulate and
 * exhaust resources), while a complete, well-behaved request is unaffected.
 */
public class NettySlowRequestTest {

	// --- CONFIG ---

	private static final int PORT = 3000;

	/** Server-side read timeout (seconds); kept short to keep the test fast. */
	private static final int READ_TIMEOUT = 2;

	/** Client-side socket timeout; comfortably larger than READ_TIMEOUT. */
	private static final int SO_TIMEOUT_MILLIS = 8000;

	// --- MOLECULER COMPONENTS ---

	private ServiceBroker br;
	private NettyServer server;

	// --- SETUP / TEARDOWN ---

	@BeforeEach
	public void setUp() throws Exception {
		AbstractTemplateTest.waitForFreePort(PORT);

		br = ServiceBroker.builder().monitor(new ConstantMonitor()).build();

		server = new NettyServer(PORT);
		server.setReadTimeout(READ_TIMEOUT);
		br.createService(server);

		br.createService(new ApiGateway("**"));
		br.createService(new Service("math") {

			@SuppressWarnings("unused")
			public Action add = ctx -> ctx.params.get("a", 0) + ctx.params.get("b", 0);

		});

		br.start();
	}

	@AfterEach
	public void tearDown() throws Exception {
		if (br != null) {
			br.stop();
			br = null;
		}
	}

	// --- TESTS ---

	/**
	 * Incomplete header block (no terminating CRLFCRLF), then silence: the
	 * server must close the connection within the read timeout.
	 */
	@Test
	public void testIncompleteHeadersAreTimedOut() throws Exception {
		try (Socket socket = new Socket("127.0.0.1", PORT)) {
			socket.setSoTimeout(SO_TIMEOUT_MILLIS);
			OutputStream os = socket.getOutputStream();

			// Send only part of the header block - never the closing blank line.
			os.write("GET /math/add?a=3&b=4 HTTP/1.1\r\nHost: 127.0.0.1\r\n".getBytes(StandardCharsets.UTF_8));
			os.flush();

			assertClosedByServer(socket, "incomplete headers");
		}
	}

	/**
	 * Full headers announcing a body via Content-Length, but only a fragment of
	 * the body is sent and then the client goes silent: the server must close
	 * the connection within the read timeout.
	 */
	@Test
	public void testIncompleteBodyIsTimedOut() throws Exception {
		try (Socket socket = new Socket("127.0.0.1", PORT)) {
			socket.setSoTimeout(SO_TIMEOUT_MILLIS);
			OutputStream os = socket.getOutputStream();

			// Complete header block promising 1000 body bytes...
			os.write(("POST /math/add HTTP/1.1\r\n" + "Host: 127.0.0.1\r\n" + "Content-Type: application/json\r\n"
					+ "Content-Length: 1000\r\n\r\n").getBytes(StandardCharsets.UTF_8));
			// ...but send only a fragment and then stall forever.
			os.write("{\"a\":1,".getBytes(StandardCharsets.UTF_8));
			os.flush();

			assertClosedByServer(socket, "incomplete body");
		}
	}

	/**
	 * A complete, fast request must succeed and must NOT be closed by the read
	 * timeout (guards against the hardening breaking normal traffic).
	 */
	@Test
	public void testCompleteRequestSucceeds() throws Exception {
		try (Socket socket = new Socket("127.0.0.1", PORT)) {
			socket.setSoTimeout(SO_TIMEOUT_MILLIS);
			OutputStream os = socket.getOutputStream();
			InputStream is = socket.getInputStream();

			os.write(("GET /math/add?a=3&b=4 HTTP/1.1\r\n" + "Host: 127.0.0.1\r\n" + "Connection: close\r\n\r\n")
					.getBytes(StandardCharsets.UTF_8));
			os.flush();

			String statusLine = readStatusLine(is);
			assertTrue(statusLine.startsWith("HTTP/1.1 200"), "expected 200 OK, got: " + statusLine);
		}
	}

	// --- HELPERS ---

	/**
	 * Asserts that the server reclaims the connection (EOF) before the client
	 * socket timeout elapses. The server may first flush an error response (e.g.
	 * when a half-received body aborts the in-flight action) and then close, so
	 * any preceding bytes are drained; the security property under test is that
	 * the connection does not stay open indefinitely. If the read instead times
	 * out, the connection was left open - i.e. the Slowloris protection did not
	 * kick in.
	 */
	private static void assertClosedByServer(Socket socket, String scenario) throws Exception {
		InputStream is = socket.getInputStream();
		byte[] buffer = new byte[256];
		long start = System.currentTimeMillis();
		try {
			while (is.read(buffer) != -1) {

				// Drain any (error) response bytes until the server closes.
			}
			long elapsed = System.currentTimeMillis() - start;
			assertTrue(elapsed < SO_TIMEOUT_MILLIS,
					"[" + scenario + "] connection closed, but took too long: " + elapsed + " ms");
		} catch (SocketTimeoutException notClosed) {
			fail("[" + scenario + "] connection was NOT closed within " + SO_TIMEOUT_MILLIS
					+ " ms - the read timeout did not fire (Slowloris vulnerability).");
		}
	}

	/** Reads the first line (status line) of an HTTP response. */
	private static String readStatusLine(InputStream is) throws Exception {
		StringBuilder line = new StringBuilder(64);
		int c;
		while ((c = is.read()) != -1) {
			if (c == '\n') {
				break;
			}
			if (c != '\r') {
				line.append((char) c);
			}
		}
		return line.toString();
	}

}
