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

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import services.moleculer.web.servlet.MoleculerServlet;
import services.moleculer.web.servlet.service.BlockingService;

public class BlockingServletTest extends AbstractTemplateTest {

	protected Server server;
	
	@Override
	protected void setUp() throws Exception {	
		try {
			if (server != null) {
				server.stop();
				server = null;
			}
		} catch (Exception ignored) {
		}
		server = new Server();
		ServerConnector pContext = new ServerConnector(server);
		pContext.setHost("127.0.0.1");
		pContext.setPort(3000);
		ServletContextHandler publicContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
		publicContext.setSessionHandler(new SessionHandler());
		publicContext.setContextPath("/");
		
		// Create blocking servlet
		MoleculerServlet sc = new MoleculerServlet();
		ServletHolder sh = new ServletHolder(sc);
		sh.setInitParameter("moleculer.config", "/services/moleculer/web/moleculer.config.xml");
		sh.setInitParameter("moleculer.force.blocking", "true");
		publicContext.addServlet(sh, "/*");
		HandlerCollection collection = new HandlerCollection();
		collection.addHandler(publicContext);
		server.setHandler(collection);
		server.addConnector(pContext);
		server.start();
				
		assertEquals(BlockingService.class, sc.getServiceMode().getClass());
		
		br = sc.getBroker();
		gw = sc.getGateway();
		
		super.setUp();
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			if (server != null) {
				server.stop();
				server = null;
			}
		} catch (Exception ignored) {
		}
		try {
			Thread.sleep(1000);
		} catch (Exception ignored) {
		}
		super.tearDown();
	}
	
}
