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

import java.io.File;
import java.math.BigInteger;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.Test;

import io.datatree.Tree;
import junit.framework.TestCase;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Action;
import services.moleculer.service.Service;
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
		r = gw.addRoute("/test", "html", new SessionCookie());
		
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
		
		// TODO install engine
		
		// Call common template tests
		doTemplateTests();
	}

	@Test
	public void testFreeMarkerTemplateEngine() throws Exception {
		FreeMarkerEngine engine = new FreeMarkerEngine();
		engine.setTemplatePath("templates");	
		engine.setDefaultExtension("freemarker");
		
		// Call common template tests
		doTemplateTests();
	}

	@Test
	public void testJadeTemplateEngine() throws Exception {
		JadeEngine engine = new JadeEngine();
		engine.setTemplatePath("templates");	
		engine.setDefaultExtension("jade");
		
		// Call common template tests
		doTemplateTests();
	}

	@Test
	public void testMustacheTemplateEngine() throws Exception {
		MustacheEngine engine = new MustacheEngine();
		engine.setTemplatePath("templates");	
		engine.setDefaultExtension("mustache");
		
		// Call common template tests
		doTemplateTests();
	}

	@Test
	public void testPebbleTemplateEngine() throws Exception {
		PebbleEngine engine = new PebbleEngine();
		engine.setTemplatePath("templates");	
		engine.setDefaultExtension("pebble");
		
		// Call common template tests
		doTemplateTests();
	}

	@Test
	public void testThymeleafTemplateEngine() throws Exception {
		ThymeleafEngine engine = new ThymeleafEngine();
		engine.setTemplatePath("templates");	
		engine.setDefaultExtension("thymeleaf");
		
		// Call common template tests
		doTemplateTests();
	}

	protected void doTemplateTests() throws Exception {

		//HttpGet req = new HttpGet("http://localhost:3000");
		HttpPost req = new HttpPost("http://localhost:3000");

		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
	    builder.addTextBody("username", "user");
	    builder.addTextBody("password", "pass");
	    builder.addBinaryBody("file", new File("c:/temp/script.txt"),
	      ContentType.TEXT_PLAIN, "test.txt");
	 
	    HttpEntity multipart = builder.build();
	    req.setEntity(multipart);
		
	    HttpResponse rsp = cl.execute(req, null).get();
	    
	    System.out.println(rsp);
	}
	
}
