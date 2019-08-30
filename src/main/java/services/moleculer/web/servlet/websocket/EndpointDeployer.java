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
package services.moleculer.web.servlet.websocket;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndpointDeployer implements ServletContextListener {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(EndpointDeployer.class);

	// --- INIT ---
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		ServletContext ctx = sce.getServletContext();
		ServerContainer container = (ServerContainer) ctx.getAttribute(ServerContainer.class.getName());
		if (container == null) {
			throw new IllegalStateException("ServerContainer is null!");
		}
		String pathPrefix = ctx.getInitParameter("moleculer.websocket.path.prefix");
		StringBuilder path = new StringBuilder(64);
		if (pathPrefix != null && !pathPrefix.isEmpty()) {
			path.append(pathPrefix);
		}
		int pathLength = Integer.parseInt(System.getProperty("moleculer.path.length", "8"));
		EndpointConfigurator configurator = new EndpointConfigurator();
		ctx.setAttribute("moleculer.endpoint.configurator", configurator);
		ServletWebSocketRegistry registry = (ServletWebSocketRegistry) ctx.getAttribute("moleculer.servlet.registry");
		if (registry != null) {
			configurator.setServletWebSocketRegistry(registry);
		}
		try {
			for (int i = 0; i < pathLength; i++) {
				path.append("/{p").append(i).append('}');
				container.addEndpoint(ServerEndpointConfig.Builder.create(WebSocketListener.class, path.toString())
						.configurator(configurator).build());
			}
			logger.info("WebSocket handler installed successfully.");
		} catch (DeploymentException cause) {
			logger.error("Unable to deploy endpoint!", cause);
		}
	}

	// --- STOP ---
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		
		// Do nothing
	}

}
