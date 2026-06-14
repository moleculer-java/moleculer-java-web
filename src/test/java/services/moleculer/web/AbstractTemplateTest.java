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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.datatree.Tree;
import io.datatree.dom.BASE64;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Action;
import services.moleculer.service.Service;
import services.moleculer.stream.PacketStream;
import services.moleculer.web.common.HttpConstants;
import services.moleculer.web.middleware.BasicAuthenticator;
import services.moleculer.web.middleware.CorsHeaders;
import services.moleculer.web.middleware.Favicon;
import services.moleculer.web.middleware.NotFound;
import services.moleculer.web.middleware.RateLimiter;
import services.moleculer.web.middleware.Redirector;
import services.moleculer.web.middleware.RequestLogger;
import services.moleculer.web.middleware.ResponseDeflater;
import services.moleculer.web.middleware.ResponseHeaders;
import services.moleculer.web.middleware.ResponseTime;
import services.moleculer.web.middleware.ResponseTimeout;
import services.moleculer.web.middleware.ServeStatic;
import services.moleculer.web.middleware.session.SessionCookie;
import services.moleculer.web.middleware.session.SessionHandler;
import services.moleculer.web.router.Alias;
import services.moleculer.web.router.MappingPolicy;
import services.moleculer.web.router.Route;
import services.moleculer.web.template.DataTreeEngine;
import services.moleculer.web.template.FreeMarkerEngine;
import services.moleculer.web.template.HandlebarsEngine;
import services.moleculer.web.template.MustacheEngine;
import services.moleculer.web.template.PebbleEngine;
import services.moleculer.web.template.ThymeleafEngine;
import services.moleculer.web.template.VelocityEngine;
import services.moleculer.web.template.languages.DefaultMessageLoader;

public abstract class AbstractTemplateTest {

	protected ServiceBroker br;
	protected ApiGateway gw;
	protected CloseableHttpAsyncClient cl;

	// --- CONNECTOR LIFECYCLE (implemented by subclasses) ---

	/**
	 * Builds the {@link #br broker} + {@link #gw gateway} and starts the
	 * connector (Netty or a servlet container). Runs before the shared routes
	 * are installed below.
	 */
	protected abstract void startServer() throws Exception;

	/**
	 * Stops the connector started by {@link #startServer()}.
	 */
	protected abstract void stopServer() throws Exception;

	/**
	 * Each connector test binds port 3000; the suite runs them sequentially, so
	 * wait until a previous test has actually released the OS socket before the
	 * next one tries to bind (avoids intermittent "address already in use" /
	 * "connection refused").
	 */
	public static void waitForFreePort(int port) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 15000;
		while (System.currentTimeMillis() < deadline) {
			try (ServerSocket probe = new ServerSocket()) {
				probe.setReuseAddress(true);
				probe.bind(new InetSocketAddress(port));
				return;
			} catch (IOException notFreeYet) {
				Thread.sleep(100);
			}
		}
	}

	@BeforeEach
	public void setUp() throws Exception {
		waitForFreePort(3000);
		startServer();

		br.createService(new Service("test") {

			@SuppressWarnings("unused")
			Action html = ctx -> {

				Tree rsp = new Tree();
				rsp.put("a", 1);
				rsp.put("b", true);
				rsp.put("c", "xyz");
				rsp.put("d.e", new BigInteger("3210"));

				Tree table = rsp.putList("table");
				for (int i = 0; i < 10; i++) {
					Tree row = table.addMap();
					row.put("first", "12345");
					row.put("second", i % 2 == 0);
					row.put("third", i);
				}

				// Get requested language/locale from
				// the input parameters (from the URL)
				String locale = ctx.params.get("locale", "en");

				// Set language/locale of the template
				Tree meta = rsp.getMeta();
				meta.put(HttpConstants.META_LOCALE, locale);

				// Set template file (test.xxx)
				meta.put(HttpConstants.META_TEMPLATE, "test");
				return rsp;
			};
		});

		br.createService(new Service("math") {

			@SuppressWarnings("unused")
			Action add = ctx -> {
				int c = ctx.params.get("a", 0) + ctx.params.get("b", 0);
				if (c == -1) {
					Thread.sleep(2000);
				}
				ctx.params.put("c", c);
				return ctx.params;
			};

			@SuppressWarnings("unused")
			Action first = ctx -> {
				return ctx.params.put("src", "first");
			};

			@SuppressWarnings("unused")
			Action second = ctx -> {
				return ctx.params.put("src", "second");
			};

		});

		br.createService(new Service("session") {

			@SuppressWarnings("unused")
			Action check = ctx -> {
				Tree meta = ctx.params.getMeta();
				Tree session = meta.get("$session");
				Tree storeit = ctx.params.get("storeit");
				if (storeit != null) {
					session.copyFrom(storeit);
				}
				return session;
			};

		});

		br.createService(new ChunkedService());

		gw.use(new RequestLogger());
		gw.use(new Favicon());

		// Create authenticated route
		Route r0 = new Route();

		r0.use(new BasicAuthenticator("testuser", "testpassword"));

		r0.addAlias(Alias.GET, "/auth", "math.add");

		r0.use(new ResponseHeaders("Test-Header", "Test-Value"));

		r0.use(new ResponseTime("Response-Time"));

		r0.use(new RateLimiter(10, true));

		r0.use(new ResponseTimeout(700));

		gw.addRoute(r0);

		// REST route
		Route r1 = new Route();

		r1.addAlias(Alias.GET, "/api/users/:a/any", "math.first");
		r1.addAlias(Alias.GET, "/api/users/:b/change-password", "math.second");

		r1.addAlias(Alias.GET, "/math/add/:a/:b", "math.add");
		r1.addAlias(Alias.GET, "/math/addshort/:a", "math.add");
		r1.use(new CorsHeaders());

		// Add deflater to REST service
		ResponseDeflater deflater = new ResponseDeflater();
		r1.use(deflater);

		// Template engine test with locale test
		r1.addAlias(Alias.GET, "/html/:locale", "test.html");
		r1.use(new SessionCookie("SID", "/html"));

		// Set SessionHandler
		SessionHandler sessionHandler = new SessionHandler(br);
		gw.setBeforeCall(sessionHandler.beforeCall());
		gw.setAfterCall(sessionHandler.afterCall());

		// Chunked test
		r1.addAlias(Alias.POST, "/chunked/stream", "chunkedService.stream");
		r1.addAlias(Alias.POST, "/chunked/rest", "chunkedService.rest");

		// Session test
		r1.addAlias(Alias.POST, "/session", "session.check");

		gw.addRoute(r1);

		// Create route for serving html content
		Route r2 = new Route();

		// Enable all requests (not just the aliases or whitelist entries)
		r2.setMappingPolicy(MappingPolicy.ALL);

		// Last handler must be the "404 not found" middleware
		r2.use(new NotFound("<html>NOT FOUND</html>"));

		// Add static page (html, images, javascript) folder
		r2.use(new ServeStatic("/static", "/www"));

		// ...and a sample Redirector middleware
		r2.use(new Redirector("/missing", "/index.html", 307));

		gw.addRoute(r2);

		// Content compression is disabled so the deflate test below can inspect
		// the raw (still-compressed) response body and the Content-Encoding header.
		// A generous connection pool keeps rapid sequential requests (e.g. the
		// rate-limiter loop) from blocking on connection leases.
		PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
				.setMaxConnTotal(64).setMaxConnPerRoute(64).build();
		cl = HttpAsyncClients.custom().disableContentCompression().setConnectionManager(cm).build();
		cl.start();
	}

	@AfterEach
	public void tearDown() throws Exception {
		try {
			stopServer();
		} catch (Exception ignored) {
		}
		if (cl != null) {
			cl.close();
			cl = null;
		}
		if (br != null) {
			br.stop();
			br = null;
		}
	}

	// ---------------- TESTS ----------------

	@Test
	public void testDataTreeTemplateEngine() throws Exception {
		DataTreeEngine engine = new DataTreeEngine();
		engine.setMessageLoader(new DefaultMessageLoader());
		engine.setTemplatePath("www");
		engine.setDefaultExtension("datatree");
		gw.setTemplateEngine(engine);
		doTemplateTests("datatree");
		doTemplateTests("datatree");
	}

	@Test
	public void testFreeMarkerTemplateEngine() throws Exception {
		FreeMarkerEngine engine = new FreeMarkerEngine();
		engine.setMessageLoader(new DefaultMessageLoader());
		engine.setTemplatePath("www");
		engine.setDefaultExtension("freemarker");
		gw.setTemplateEngine(engine);
		doTemplateTests("freemarker");
		doTemplateTests("freemarker");
	}

	@Test
	public void testMustacheTemplateEngine() throws Exception {
		MustacheEngine engine = new MustacheEngine();
		engine.setMessageLoader(new DefaultMessageLoader());
		engine.setTemplatePath("www");
		engine.setDefaultExtension("mustache");
		gw.setTemplateEngine(engine);
		doTemplateTests("mustache");
		doTemplateTests("mustache");
	}

	@Test
	public void testHandlebarsTemplateEngine() throws Exception {
		HandlebarsEngine engine = new HandlebarsEngine();
		engine.setMessageLoader(new DefaultMessageLoader());
		engine.setTemplatePath("www");
		engine.setDefaultExtension("handlebars");
		gw.setTemplateEngine(engine);
		doTemplateTests("handlebars");
		doTemplateTests("handlebars");
	}

	@Test
	public void testPebbleTemplateEngine() throws Exception {
		PebbleEngine engine = new PebbleEngine();
		engine.setMessageLoader(new DefaultMessageLoader());
		engine.setTemplatePath("www");
		engine.setDefaultExtension("pebble");
		gw.setTemplateEngine(engine);
		doTemplateTests("pebble");
		doTemplateTests("pebble");
	}

	@Test
	public void testThymeleafTemplateEngine() throws Exception {
		ThymeleafEngine engine = new ThymeleafEngine();
		engine.setMessageLoader(new DefaultMessageLoader());
		engine.setTemplatePath("www");
		engine.setDefaultExtension("thymeleaf");
		gw.setTemplateEngine(engine);
		doTemplateTests("thymeleaf");
		doTemplateTests("thymeleaf");
	}

	@Test
	public void testVelocityTemplateEngine() throws Exception {
		VelocityEngine engine = new VelocityEngine();
		engine.setMessageLoader(new DefaultMessageLoader());
		engine.setTemplatePath("www");
		engine.setDefaultExtension("velocity");
		engine.setReloadable(false);
		gw.setTemplateEngine(engine);
		doTemplateTests("velocity");
		doTemplateTests("velocity");
	}

	@Test
	public void testChunked() throws Exception {

		// Chunked (Transfer-Encoding: chunked, no Content-Length) octet-stream
		// upload: the gateway exposes it to the action as ctx.stream and streams
		// the echoed bytes back.
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int i = 0; i < 10000; i++) {
			out.write(i % 128);
		}
		byte[] bytes = postChunked("http://127.0.0.1:3000/chunked/stream", out.toByteArray(),
				"application/octet-stream");
		for (int i = 0; i < 10000; i++) {
			assertEquals(i % 128, bytes[i]);
		}

		// Chunked JSON body parsed into params and echoed back
		Tree t = new Tree();
		for (int i = 0; i < 10; i++) {
			t.put("key" + i, "value" + i);
		}
		bytes = postChunked("http://127.0.0.1:3000/chunked/rest", t.toBinary(), "application/json");
		Tree r = new Tree(bytes);
		for (int i = 0; i < 10; i++) {
			assertEquals("value" + i, r.get("key" + i, ""));
		}
	}

	private byte[] postChunked(String url, byte[] body, String contentType) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setRequestMethod("POST");
		c.setDoOutput(true);
		c.setChunkedStreamingMode(1000);
		c.setConnectTimeout(10000);
		c.setReadTimeout(300000);
		c.setRequestProperty("Content-Type", contentType);
		try (OutputStream os = c.getOutputStream()) {
			os.write(body);
		}
		assertEquals(200, c.getResponseCode());
		try (InputStream is = c.getInputStream()) {
			return is.readAllBytes();
		}
	}

	public static class ChunkedService extends Service {

		public Action stream = ctx -> {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			return ctx.stream.transferTo(out).then(rsp -> {
				PacketStream stream = ctx.createStream();
				stream.setPacketSize(1000);
				stream.setPacketDelay(10);
				stream.transferFrom(new ByteArrayInputStream(out.toByteArray()));
				return stream;
			});
		};

		public Action rest = ctx -> {
			return ctx.params;
		};

	}

	@Test
	public void testPath() throws Exception {
		SimpleHttpRequest get = SimpleRequestBuilder.get("http://127.0.0.1:3000/api/users/4/any").build();
		SimpleHttpResponse rsp = cl.execute(get, null).get();
		String txt = new String(rsp.getBodyBytes(), StandardCharsets.UTF_8);
		assertTrue(txt.contains("first"));

		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/api/users/3/change-password").build();
		rsp = cl.execute(get, null).get();
		System.out.println(rsp.getCode());
		txt = new String(rsp.getBodyBytes(), StandardCharsets.UTF_8);
		assertTrue(txt.contains("second"));
	}

	@Test
	public void testMiddlewares() throws Exception {

		// Session test
		Tree t = checkSession(new Tree().put("a", 3));
		assertEquals(3, t.get("a", 0));

		t = checkSession(new Tree());
		assertEquals(3, t.get("a", 0));

		t = checkSession(new Tree().put("b", "xyz"));
		assertEquals(3, t.get("a", 0));
		assertEquals("xyz", t.get("b", ""));

		t = checkSession(new Tree());
		assertEquals(3, t.get("a", 0));
		assertEquals("xyz", t.get("b", ""));

		// First load
		SimpleHttpRequest get = SimpleRequestBuilder.get("http://127.0.0.1:3000/static/index.html").build();
		SimpleHttpResponse rsp = cl.execute(get, null).get();

		assertEquals(200, rsp.getCode());
		String etag1 = rsp.getLastHeader("ETag").getValue();

		String txt = new String(rsp.getBodyBytes(), StandardCharsets.UTF_8);

		assertTrue(txt.contains("<h1>header</h1>"));

		// Reload page (using ETags)
		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/static/index.html").build();
		get.setHeader("If-None-Match", etag1);
		rsp = cl.execute(get, null).get();

		assertEquals(304, rsp.getCode());
		// assertEquals("0", rsp.getLastHeader("Content-Length").getValue());

		// Favicon
		get("favicon.ico", 200, "image/x-icon", null);

		// Invalid page
		get("invalid.html", 404, "text/html", "<html>NOT FOUND</html>");

		// Space in path
		get("static/space space/space space space.html", 200, "text/html", "<html>SPACE</html>");

		// Deflated REST
		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/math/add/3/4").build();
		get.setHeader("Accept-Encoding", "deflate");
		rsp = cl.execute(get, null).get();
		assertEquals(200, rsp.getCode());
		assertTrue(rsp.getLastHeader("Content-Encoding").getValue().contains("deflate"));

		txt = new String(rsp.getBodyBytes(), StandardCharsets.UTF_8);
		assertFalse(txt.contains("{"));
		assertFalse(txt.contains(","));
		assertFalse(txt.contains("3"));

		// REST without deflating
		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/math/add/3/4").build();
		rsp = cl.execute(get, null).get();
		assertEquals(200, rsp.getCode());
		assertTrue(rsp.getLastHeader("Content-Encoding") == null);

		txt = new String(rsp.getBodyBytes(), StandardCharsets.UTF_8);
		assertTrue(txt.contains("{"));
		assertTrue(txt.contains(","));
		assertTrue(txt.contains("3"));

		// Invoke authenticated method
		get("auth", 401, null, null);

		// Invoke authenticated method with userid/password
		String secret = "BASIC " + new String(BASE64.encode("testuser:testpassword".getBytes()));
		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/auth?a=1&b=2").build();
		get.setHeader("Authorization", secret);
		rsp = cl.execute(get, null).get();
		assertEquals(200, rsp.getCode());
		txt = new String(rsp.getBodyBytes(), StandardCharsets.UTF_8);
		assertTrue(txt.contains("{"));
		assertTrue(txt.contains(","));
		assertTrue(txt.contains("3"));

		// Custom response header test
		assertEquals("Test-Value", rsp.getLastHeader("Test-Header").getValue());

		// Response time test
		String time = rsp.getLastHeader("Response-Time").getValue();
		assertTrue(time.contains("ms"));
		assertTrue(Integer.parseInt(time.substring(0, time.length() - 2)) >= 0);

		// Rate limiter test. The default rate window is 1 second, so all of
		// these requests must land in a single window. They are driven with a
		// plain (keep-alive, synchronous) HttpURLConnection: the async client's
		// connection-release lag can stall an individual rapid request long
		// enough to cross the window boundary and reset the counter.
		Thread.sleep(1500);
		for (int n = 9; n > -2; n--) {
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:3000/auth?a=1&b=2").openConnection();
			c.setRequestProperty("Authorization", secret);
			int code = c.getResponseCode();
			int remaining = Integer.parseInt(c.getHeaderField("X-Rate-Limit-Remaining"));
			try (InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream()) {
				if (is != null) {
					is.readAllBytes();
				}
			}
			if (n < 0) {
				assertEquals(0, remaining);
				assertEquals("0", c.getHeaderField("Content-Length"));
				assertEquals(429, code);
			} else {
				assertEquals(n, remaining);
			}
		}

		// Response timeout
		Thread.sleep(1500);
		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/auth?a=2&b=-3").build();
		get.setHeader("Authorization", secret);
		rsp = cl.execute(get, null).get();
		assertEquals(408, rsp.getCode());
	}

	private final void get(String path, Integer requiredCode, String requiredType, String requiredText)
			throws Exception {
		path = "http://127.0.0.1:3000/" + path;
		SimpleHttpRequest get = SimpleRequestBuilder.get(path.replace(" ", "%20")).build();
		SimpleHttpResponse rsp = cl.execute(get, null).get();
		if (requiredCode != null) {
			assertEquals(requiredCode.intValue(), rsp.getCode());
		}
		if (requiredType != null) {
			assertTrue(rsp.getLastHeader("Content-Type").getValue().contains(requiredType));
		}
		byte[] bytes = rsp.getBodyBytes();
		if (requiredText != null) {
			String txt = new String(bytes == null ? new byte[0] : bytes, StandardCharsets.UTF_8);
			assertTrue(txt.contains(requiredText));
		}
	}

	// --- COMMON TESTS ---

	protected void doTemplateTests(String name) throws Exception {

		SimpleHttpRequest get = SimpleRequestBuilder.get("http://127.0.0.1:3000/html/en").build();
		SimpleHttpResponse rsp = cl.execute(get, null).get();

		Header[] headers = rsp.getHeaders("Set-Cookie");
		boolean found = false;
		for (Header header : headers) {
			if (header.getValue().contains("SID=")) {
				found = true;
				break;
			}
		}
		if (!found) {
			fail("SID cookie not found!");
		}

		assertEquals(200, rsp.getCode());
		String html = new String(rsp.getBodyBytes(), StandardCharsets.UTF_8);

		// #1.) Multilanguage (default language)
		assertTrue(html.contains("<li>Ok"));
		assertTrue(html.contains("<li>Hello!"));
		assertTrue(html.contains("<li>Goodbye!"));
		assertTrue(html.contains("<li>How are you?"));

		// Table
		assertTrue(html.contains("<h1>header</h1>"));
		assertTrue(html.contains(name));
		if ("thymeleaf".equals(name)) {
			assertTrue(html.contains("<p>1</p>"));
			assertTrue(html.contains("<p>true</p>"));
			assertTrue(html.contains("<p>xyz</p>"));
			assertTrue(html.contains("<p>3210</p>"));
		} else {
			assertTrue(html.contains("<p>A: 1</p>"));
			assertTrue(html.contains("<p>B: true</p>"));
			assertTrue(html.contains("<p>C: xyz</p>"));
			assertTrue(html.contains("<p>D.E: 3210</p>") || html.contains("<p>D.E: 3,210</p>"));
		}

		for (int i = 0; i < 10; i++) {
			assertTrue(html.contains("<td>" + i + "</td>"));
		}

		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/math/add/1/2").build();
		rsp = cl.execute(get, null).get();

		String header = rsp.getLastHeader("Access-Control-Allow-Origin").getValue();
		assertEquals("*", header);
		header = rsp.getLastHeader("Access-Control-Allow-Methods").getValue();
		assertEquals("GET,OPTIONS,POST,PUT,DELETE", header);

		String json = new String(rsp.getBodyBytes(), StandardCharsets.UTF_8);
		Tree t = new Tree(json);
		assertEquals("1", t.get("a", ""));
		assertEquals("2", t.get("b", ""));
		assertEquals("3", t.get("c", ""));

		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/math/addshort/11").build();
		rsp = cl.execute(get, null).get();
		t = new Tree(new String(rsp.getBodyBytes(), StandardCharsets.UTF_8));
		assertEquals("11", t.get("a", ""));
		assertNull(t.get("b"));
		assertEquals("11", t.get("c", ""));

		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/math/addshort/11?b=22").build();
		rsp = cl.execute(get, null).get();
		t = new Tree(new String(rsp.getBodyBytes(), StandardCharsets.UTF_8));
		assertEquals("11", t.get("a", ""));
		assertEquals("22", t.get("b", ""));
		assertEquals("33", t.get("c", ""));

		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/math/add/11/22?b=33").build();
		rsp = cl.execute(get, null).get();
		t = new Tree(new String(rsp.getBodyBytes(), StandardCharsets.UTF_8));
		assertEquals("11", t.get("a", ""));
		assertEquals("33", t.get("b", ""));
		assertEquals("44", t.get("c", ""));

		// #2.) Multilanguage (French language)
		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/html/fr").build();
		rsp = cl.execute(get, null).get();
		assertEquals(200, rsp.getCode());
		html = new String(rsp.getBodyBytes(), StandardCharsets.UTF_8);

		assertTrue(html.contains("<li>Ok"));
		assertTrue(html.contains("<li>Bonjour!"));
		assertTrue(html.contains("<li>Au revoir!"));
		assertTrue(html.contains("<li>Comment allez-vous?"));

		// #3.) Multilanguage (Canadian French)
		get = SimpleRequestBuilder.get("http://127.0.0.1:3000/html/fr-ca").build();
		rsp = cl.execute(get, null).get();
		assertEquals(200, rsp.getCode());
		html = new String(rsp.getBodyBytes(), StandardCharsets.UTF_8);

		assertTrue(html.contains("<li>Ok"));
		assertTrue(html.contains("<li>Bonjour!"));
		assertTrue(html.contains("<li>Au revoir!"));
		assertTrue(html.contains("<li>Comment vas-tu?"));
	}

	protected Tree checkSession(Tree storeit) throws Exception {
		Tree body = new Tree();
		body.putObject("storeit", storeit);
		SimpleHttpRequest post = SimpleRequestBuilder.post("http://127.0.0.1:3000/session")
				.setBody(body.toBinary(), ContentType.DEFAULT_BINARY).build();
		SimpleHttpResponse rsp = cl.execute(post, null).get();
		Header[] headers = rsp.getHeaders("Set-Cookie");
		boolean found = false;
		for (Header header : headers) {
			if (header.getValue().contains("SID=")) {
				found = true;
				break;
			}
		}
		if (!found) {
			fail("SID cookie not found!");
		}
		assertEquals(200, rsp.getCode());
		return new Tree(rsp.getBodyBytes());
	}

}
