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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.Test;

import io.datatree.Tree;
import junit.framework.TestCase;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Action;
import services.moleculer.service.Service;
import services.moleculer.util.CommonUtils;
import services.moleculer.web.middleware.CorsHeaders;
import services.moleculer.web.middleware.Favicon;
import services.moleculer.web.middleware.SessionCookie;
import services.moleculer.web.router.Route;
import services.moleculer.web.template.DataTreeEngine;
import services.moleculer.web.template.FreeMarkerEngine;
import services.moleculer.web.template.JadeEngine;
import services.moleculer.web.template.MustacheEngine;
import services.moleculer.web.template.PebbleEngine;
import services.moleculer.web.template.ThymeleafEngine;

public abstract class AbstractTemplateTest extends TestCase {

	protected ServiceBroker br;
	protected ApiGateway gw;
	protected CloseableHttpAsyncClient cl;
	protected Route r;

	@Override
	protected void setUp() throws Exception {
		br.createService(new Service("test") {

			@SuppressWarnings("unused")
			public Action html = ctx -> {

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

				rsp.getMeta().put("$template", "test");
				return rsp;
			};
		});

		br.createService(new Service("math") {

			@SuppressWarnings("unused")
			public Action add = ctx -> {
				int c = ctx.params.get("a", 0) + ctx.params.get("b", 0);
				ctx.params.put("c", c);
				return ctx.params;
			};

		});

		gw.use(new Favicon());
		r = gw.addRoute("GET", "/html", "test.html", new SessionCookie());
		r = gw.addRoute("GET", "/math/add/:a/:b", "math.add", new CorsHeaders());

		cl = HttpAsyncClients.createDefault();
		cl.start();
	}

	@Override
	protected void tearDown() throws Exception {
		if (br != null) {
			br.stop();
		}
		if (cl != null) {
			cl.close();
		}
	}

	// ---------------- TESTS ----------------

	@Test
	public void testDataTreeTemplateEngine() throws Exception {
		DataTreeEngine engine = new DataTreeEngine();
		engine.setTemplatePath("templates");
		engine.setDefaultExtension("datatree");
		gw.setTemplateEngine(engine);
		doTemplateTests("datatree");
	}

	@Test
	public void testFreeMarkerTemplateEngine() throws Exception {
		FreeMarkerEngine engine = new FreeMarkerEngine();
		engine.setTemplatePath("templates");
		engine.setDefaultExtension("freemarker");
		gw.setTemplateEngine(engine);
		doTemplateTests("freemarker");
	}

	@Test
	public void testJadeTemplateEngine() throws Exception {
		JadeEngine engine = new JadeEngine();
		engine.setTemplatePath("templates");
		engine.setDefaultExtension("jade");
		gw.setTemplateEngine(engine);
		doTemplateTests("jade");
	}

	@Test
	public void testMustacheTemplateEngine() throws Exception {
		MustacheEngine engine = new MustacheEngine();
		engine.setTemplatePath("templates");
		engine.setDefaultExtension("mustache");
		gw.setTemplateEngine(engine);
		doTemplateTests("mustache");
	}

	@Test
	public void testPebbleTemplateEngine() throws Exception {
		PebbleEngine engine = new PebbleEngine();
		engine.setTemplatePath("templates");
		engine.setDefaultExtension("pebble");
		gw.setTemplateEngine(engine);
		doTemplateTests("pebble");
	}

	@Test
	public void testThymeleafTemplateEngine() throws Exception {
		ThymeleafEngine engine = new ThymeleafEngine();
		engine.setTemplatePath("templates");
		engine.setDefaultExtension("thymeleaf");
		gw.setTemplateEngine(engine);
		doTemplateTests("thymeleaf");
	}

	// --- COMMON TESTS ---

	protected void doTemplateTests(String name) throws Exception {

		HttpGet get = new HttpGet("http://localhost:3000/html");
		HttpResponse rsp = cl.execute(get, null).get();
		String header = rsp.getLastHeader("Set-Cookie").getValue();

		// JSESSIONID="1|1c4xy2u8fjab3";$Path="/"
		assertTrue(header.startsWith("JSESSIONID=\""));
		assertTrue(header.endsWith("\";$Path=\"/\""));

		assertEquals(200, rsp.getStatusLine().getStatusCode());
		byte[] bytes = CommonUtils.readFully(rsp.getEntity().getContent());
		String html = new String(bytes, StandardCharsets.UTF_8);

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
		
		get = new HttpGet("http://localhost:3000/math/add/1/2");
		rsp = cl.execute(get, null).get();
		
		header = rsp.getLastHeader("Access-Control-Allow-Origin").getValue();
		assertEquals("*", header);
		header = rsp.getLastHeader("Access-Control-Allow-Methods").getValue();
		assertEquals("GET,OPTIONS,POST,PUT,DELETE", header);
		
		bytes = CommonUtils.readFully(rsp.getEntity().getContent());
		String json = new String(bytes, StandardCharsets.UTF_8);
		Tree t = new Tree(json);
		assertEquals("1", t.get("a", ""));
		assertEquals("2", t.get("b", ""));
		assertEquals("3", t.get("c", ""));
	}

}
