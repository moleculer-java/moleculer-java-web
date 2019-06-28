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
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Action;
import services.moleculer.service.Service;
import services.moleculer.web.router.StaticRoute;
import services.moleculer.web.servlet.AsyncMoleculerServlet;

public class SampleWithJetty {

	public static void main(String[] args) {
		System.out.println("START");
		try {

			Server server = new Server();
			ServerConnector serverConnector = new ServerConnector(server);
			serverConnector.setHost("127.0.0.1");
			serverConnector.setPort(3000);
			ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
			servletContextHandler.setContextPath("/");

			// Create non-blocking servlet
			AsyncMoleculerServlet servlet = new AsyncMoleculerServlet();
			ServletHolder servletHolder = new ServletHolder(servlet);

			servletHolder.setInitParameter("moleculer.config", "/services/moleculer/web/moleculer.config.xml");
			servletContextHandler.addServlet(servletHolder, "/*");

			HandlerCollection handlerCollection = new HandlerCollection();
			handlerCollection.addHandler(servletContextHandler);
			server.setHandler(handlerCollection);
			server.addConnector(serverConnector);

			server.start();

			ServiceBroker broker = servlet.getBroker();
			broker.repl();
			ApiGateway gateway = servlet.getGateway();

			// REST services
			// RestRoute rest = new RestRoute();
			// rest.addAlias("/test", "test.send");
			// gateway.addRoute(rest);
			
			// Static web content
			StaticRoute route = new StaticRoute("/templates");
			route.addAlias("/test", "test.send");
			route.setEnableReloading(true);
			gateway.addRoute(route);
			
			broker.createService(new Service("test") {

				@SuppressWarnings("unused")
				public Action send = ctx -> {

					Tree packet = new Tree();
					packet.put("path", "/ws/test");
					packet.put("data", 123);

					ctx.broadcast("websocket.send", packet);

					return "Data submitted!";
				};

			});

		} catch (Exception cause) {
			cause.printStackTrace();
		}
	}

}