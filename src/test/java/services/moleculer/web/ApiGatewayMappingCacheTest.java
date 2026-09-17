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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.monitor.ConstantMonitor;
import services.moleculer.service.Action;
import services.moleculer.service.Name;
import services.moleculer.service.Service;
import services.moleculer.stream.PacketStream;
import services.moleculer.web.router.HttpAlias;
import services.moleculer.web.router.MappingPolicy;
import services.moleculer.web.router.Route;

/**
 * Regression test for the "stuck 404" bug: a request that arrives while the
 * broker is still starting up gets served by a catch-all (MappingPolicy.ALL)
 * route, and the resulting Mapping used to stay in the gateway mapping cache
 * forever - so that very same URL kept answering 404 for the whole lifetime of
 * the server, even long after the real REST alias had been deployed. Only a
 * restart helped, while every other (not yet requested) URL worked fine.
 */
public class ApiGatewayMappingCacheTest {

	protected ServiceBroker br;
	protected ApiGateway gw;
	protected Route restRoute;

	// --- TESTS ---

	/**
	 * The alias is added directly on the Route (the
	 * "gw.addRoute(route).addAlias(...)" pattern) after the gateway has already
	 * answered - and cached - a request for that path.
	 */
	@Test
	public void testAliasAddedAfterCachedMiss() throws Exception {
		String path = "/api/late/ping";

		// 1. Boot window: no such action yet, so the catch-all route answers
		// 404 - and used to poison the mapping cache with it.
		assertEquals(404, call("GET", path).status);

		// 2. The service and its alias show up a moment later.
		br.createService(new LateService());
		restRoute.addAlias("GET", path, "late.ping");

		// 3. The very same URL must heal itself - without a restart.
		Response rsp = call("GET", path);
		assertEquals(200, rsp.status);
		assertTrue(rsp.text().contains("pong"), "unexpected body: " + rsp.text());
	}

	/**
	 * The same scenario through the HttpAlias auto-deployer - this is what a
	 * real application hits, because actions annotated with HttpAlias only get
	 * their route registered when the "services.changed" event fires.
	 */
	@Test
	public void testAutoDeployedAliasAfterCachedMiss() throws Exception {
		String path = "/api/auto/ping";

		// 1. Boot window: the auto-deployed alias does not exist yet.
		assertEquals(404, call("GET", path).status);

		// 2. The annotated service is deployed, so the auto-deployer adds the
		// alias to the RESTRICT route.
		br.createService(new AutoService());

		// 3. ...and the cached 404 must not survive that.
		Response rsp = call("GET", path);
		assertEquals(200, rsp.status);
		assertTrue(rsp.text().contains("pong"), "unexpected body: " + rsp.text());
	}

	/**
	 * Sanity check: a path that really has no action must keep answering 404 -
	 * the fix must not turn every miss into an error or disable caching.
	 */
	@Test
	public void testUnknownPathStays404() throws Exception {
		assertEquals(404, call("GET", "/api/nothing/here").status);
		assertEquals(404, call("GET", "/api/nothing/here").status);
	}

	// --- TEST SERVICES ---

	@Name("late")
	public static class LateService extends Service {

		public Action ping = ctx -> {
			Tree rsp = new Tree();
			rsp.put("msg", "pong");
			return rsp;
		};

	}

	@Name("auto")
	public static class AutoService extends Service {

		@HttpAlias(method = "GET", path = "/api/auto/ping")
		public Action ping = ctx -> {
			Tree rsp = new Tree();
			rsp.put("msg", "pong");
			return rsp;
		};

	}

	// --- INVOKE THE GATEWAY DIRECTLY (no HTTP server involved) ---

	protected Response call(String method, String path) throws Exception {
		Response rsp = new Response();
		gw.service(new Request(method, path), rsp);
		assertTrue(rsp.finished.await(15, TimeUnit.SECONDS), "response never finished: " + method + " " + path);
		return rsp;
	}

	protected static final class Request implements WebRequest {

		protected final String method;
		protected final String path;

		protected Request(String method, String path) {
			this.method = method;
			this.path = path;
		}

		@Override
		public String getAddress() {
			return "127.0.0.1";
		}

		@Override
		public String getMethod() {
			return method;
		}

		@Override
		public String getPath() {
			return path;
		}

		@Override
		public String getQuery() {
			return null;
		}

		@Override
		public int getContentLength() {
			return 0;
		}

		@Override
		public String getContentType() {
			return null;
		}

		@Override
		public PacketStream getBody() {
			return null;
		}

		@Override
		public String getHeader(String name) {
			return null;
		}

		@Override
		public Iterator<String> getHeaders() {
			return Collections.<String> emptyList().iterator();
		}

		@Override
		public boolean isMultipart() {
			return false;
		}

		@Override
		public String getProtocol() {
			return "HTTP/1.1";
		}

		@Override
		public Object getInternalObject() {
			return null;
		}

	}

	protected static final class Response implements WebResponse {

		protected final CountDownLatch finished = new CountDownLatch(1);
		protected final ByteArrayOutputStream body = new ByteArrayOutputStream();
		protected final Map<String, String> headers = new HashMap<>();
		protected final Map<String, Object> properties = new HashMap<>();

		protected volatile int status = 200;

		protected String text() {
			return new String(body.toByteArray(), StandardCharsets.UTF_8);
		}

		@Override
		public void setStatus(int code) {
			this.status = code;
		}

		@Override
		public int getStatus() {
			return status;
		}

		@Override
		public void setHeader(String name, String value) {
			headers.put(name, value);
		}

		@Override
		public String getHeader(String name) {
			return headers.get(name);
		}

		@Override
		public void send(byte[] bytes) throws IOException {
			if (bytes != null) {
				synchronized (body) {
					body.write(bytes);
				}
			}
		}

		@Override
		public boolean end() {
			boolean first = finished.getCount() > 0;
			finished.countDown();
			return first;
		}

		@Override
		public void setProperty(String name, Object value) {
			properties.put(name, value);
		}

		@Override
		public Object getProperty(String name) {
			return properties.get(name);
		}

		@Override
		public Object getInternalObject() {
			return null;
		}

	}

	// --- START / STOP BROKER ---

	@BeforeEach
	protected void setUp() throws Exception {
		br = ServiceBroker.builder().monitor(new ConstantMonitor()).build();

		gw = new ApiGateway();
		gw.setDebug(true);

		// Mimics a real application: a RESTRICT route for the REST actions
		// (whose aliases only arrive at runtime) followed by a catch-all ALL
		// route that serves static content - and 404s for everything else.
		restRoute = gw.addRoute(new Route());

		Route staticRoute = gw.addRoute(new Route());
		staticRoute.setMappingPolicy(MappingPolicy.ALL);

		br.createService("api-gw", gw);
		br.start();
	}

	@AfterEach
	protected void tearDown() throws Exception {
		if (br != null) {
			br.stop();
			br = null;
		}
	}

}
