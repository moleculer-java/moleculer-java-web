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
package services.moleculer.web.servlet;

import static services.moleculer.web.common.GatewayUtils.getService;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import services.moleculer.ServiceBroker;
import services.moleculer.config.ServiceBrokerConfig;
import services.moleculer.web.ApiGateway;
import services.moleculer.web.servlet.websocket.ServletWebSocketRegistry;

public abstract class AbstractMoleculerServlet extends HttpServlet {

	// --- UID ---

	private static final long serialVersionUID = -1038240217177335483L;

	// --- SERVICE BROKER'S SPRING CONTEXT ---

	protected ConfigurableApplicationContext ctx;

	// --- MOLECULER COMPONENTS ---

	protected ServiceBroker broker;
	protected ApiGateway gateway;
	protected ExecutorService executor;
	protected ScheduledExecutorService scheduler;
	
	// --- WEBSOCKET REGISTRY ---

	protected ServletWebSocketRegistry webSocketRegistry;

	protected int webSocketCleanupSeconds = 15;
	
	// --- INIT / START ---

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {

			// Start with SpringBoot
			String springApp = config.getInitParameter("moleculer.application");
			if (springApp != null && !springApp.isEmpty()) {

				// Create "args" String array by Servlet config
				Enumeration<String> e = config.getInitParameterNames();
				LinkedList<String> list = new LinkedList<>();
				while (e.hasMoreElements()) {
					String key = e.nextElement();
					String value = config.getInitParameter(key);
					if (value != null) {
						list.addLast(key);
						list.addLast(value);
					}
				}
				String[] args = new String[list.size()];
				list.toArray(args);

				// Class of the SpringApplication
				String springAppName = "org.springframework.boot.SpringApplication";
				Class<?> springAppClass = Class.forName(springAppName);

				// Input types of "run" method
				Class<?>[] types = new Class[2];
				types[0] = Class.class;
				types[1] = new String[0].getClass();
				Method m = springAppClass.getDeclaredMethod("run", types);

				// Input objects of "run" method
				Object[] in = new Object[2];
				in[0] = Class.forName(springApp);
				in[1] = args;

				// Load app with Spring Boot
				ctx = (ConfigurableApplicationContext) m.invoke(null, in);

			} else {

				// Start by using Spring XML config
				String configPath = config.getInitParameter("moleculer.config");
				if (configPath == null || configPath.isEmpty()) {
					configPath = "/WEB-INF/moleculer.config.xml";
				}
				File file = new File(configPath);
				if (file.isFile()) {
					ctx = new FileSystemXmlApplicationContext(configPath);
				} else {
					ctx = new ClassPathXmlApplicationContext(configPath);
				}
				ctx.start();
			}

			// Get ServiceBroker from Spring
			broker = ctx.getBean(ServiceBroker.class);

			// Find ApiGateway
			gateway = getService(broker, ApiGateway.class);
			if (gateway == null) {
				throw new ServletException("ApiGateway Service not defined!");
			}
			
			// Get executor and scheduler
			ServiceBrokerConfig cfg = broker.getConfig();
			executor = cfg.getExecutor();
			scheduler = cfg.getScheduler();
			
		} catch (ServletException servletException) {
			throw servletException;
		} catch (Exception fatal) {
			throw new ServletException("Unable to load Moleculer Application!", fatal);
		}
	}

	// --- DESTROY / STOP ---

	@Override
	public void destroy() {
		super.destroy();

		// Stop Atmosphere
		if (webSocketRegistry != null) {
			webSocketRegistry.stopped();
			webSocketRegistry = null;
		}

		// Stop Spring Context
		if (ctx != null) {
			try {
				ctx.stop();
			} catch (Throwable ignored) {
			}
			ctx = null;
		}
	}

	// --- GETTERS ---

	public ApiGateway getGateway() {
		return gateway;
	}

	public ServiceBroker getBroker() {
		return broker;
	}

}