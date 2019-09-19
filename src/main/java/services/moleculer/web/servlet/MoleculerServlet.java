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
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.web.ApiGateway;
import services.moleculer.web.servlet.service.BlockingService;
import services.moleculer.web.servlet.service.ServiceMode;
import services.moleculer.web.servlet.websocket.ServletWebSocketRegistry;

public class MoleculerServlet extends HttpServlet {

	// --- UID ---

	private static final long serialVersionUID = -1038240217177335483L;

	// --- SERVICE BROKER'S SPRING CONTEXT (XML-BASED OR SPRING BOOT) ---

	protected final AtomicReference<ConfigurableApplicationContext> context = new AtomicReference<>();

	// --- MOLECULER COMPONENTS ---

	protected ServiceBroker broker;
	protected ApiGateway gateway;

	// --- WEBSOCKET REGISTRY ---

	protected ServletWebSocketRegistry webSocketRegistry;

	protected int webSocketCleanupSeconds = 15;

	// --- WORKING MODE (SYNC / ASYNC) ---

	protected ServiceMode serviceMode;

	// --- OTHER VARIABLES ---

	protected long timeout;

	// --- INIT / START ---

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		if (context.get() != null) {
			return;
		}
		try {

			// Start with SpringBoot
			String springApp = config.getInitParameter("moleculer.application");
			ConfigurableApplicationContext ctx = null;
			if (springApp != null && !springApp.isEmpty()) {

				// Create "args" String array by Servlet config
				HashSet<String> set = new HashSet<>();
				Enumeration<String> e = config.getInitParameterNames();
				while (e.hasMoreElements()) {
					String key = e.nextElement();
					String value = config.getInitParameter(key);
					if (key.startsWith("-D")) {
						System.setProperty(key.substring(2), value);
					}
					if (value != null) {
						set.add(key + '=' + value);
					}
				}
				String[] args = new String[set.size()];
				set.toArray(args);

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
				context.set(ctx);

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
				context.set(ctx);
				ctx.start();
			}

			// Get ServiceBroker from Spring
			broker = ctx.getBean(ServiceBroker.class);

			// Find ApiGateway
			gateway = getService(broker, ApiGateway.class);
			if (gateway == null) {
				throw new ServletException("ApiGateway Service not defined!");
			}

			// Create WebSocket registry
			webSocketRegistry = new ServletWebSocketRegistry(config, broker, webSocketCleanupSeconds);
			gateway.setWebSocketRegistry(webSocketRegistry);

			// Blocking timeout
			String blockingTimeout = config.getInitParameter("moleculer.blocking.timeout");
			timeout = blockingTimeout == null ? 60000 * 3 : Long.parseLong(blockingTimeout);

			// Get or autodetect service mode
			if (serviceMode == null) {
				String forceBlocking = config.getInitParameter("moleculer.force.blocking");
				if (forceBlocking == null || forceBlocking.isEmpty() || "auto".equalsIgnoreCase(forceBlocking)) {
					forceBlocking = Boolean.toString(config.getClass().toString().contains("weblogic"));
				}
				if (!Boolean.parseBoolean(forceBlocking)) {
					try {
						Class.forName("javax.servlet.ReadListener");
						serviceMode = (ServiceMode) Class.forName("services.moleculer.web.servlet.service.AsyncService")
								.newInstance();
						logInfo("Moleculer is using non-blocking request processor.");
					} catch (Throwable notFound) {
					}
				}
				if (serviceMode == null) {
					serviceMode = new BlockingService(timeout);
					logInfo("Moleculer is using blocking request processor (blocking timeout: " + timeout + " msec).");
				}
			}

		} catch (ServletException servletException) {
			throw servletException;
		} catch (Exception fatal) {
			throw new ServletException("Unable to load Moleculer Application!", fatal);
		}
	}

	// --- PROCESS REQUEST ---

	@Override
	public void service(ServletRequest req, ServletResponse rsp) throws ServletException, IOException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) rsp;
		try {

			// Process GET/POST/PUT/etc. requests
			serviceMode.service(broker, gateway, request, response);

		} catch (IllegalStateException illegalState) {

			// Switching back to blocking mode
			if (serviceMode.getClass().getName().contains("Async")) {
				logInfo("IllegalStateException occured, switching back to blocking mode "
						+ " (hint: set the 'moleculer.force.blocking' Servlet parameter to 'true').");
				serviceMode = new BlockingService(timeout);

				// Repeat processing
				try {
					serviceMode.service(broker, gateway, request, response);
				} catch (Throwable cause) {
					handleError(response, cause);
				}

			} else {
				handleError(response, illegalState);
			}
		} catch (Throwable unknownError) {
			handleError(response, unknownError);
		}
	}

	// --- LOG ERROR ---

	protected void handleError(HttpServletResponse response, Throwable cause) {
		try {
			if (gateway == null) {
				response.sendError(404);
				logError("APIGateway Moleculer Service not found!", cause);
			} else {
				response.sendError(500);
				logError("Unable to process request!", cause);
			}
		} catch (Throwable ignored) {
		}
	}

	protected void logInfo(String message) {
		if (broker != null) {
			broker.getLogger(MoleculerServlet.class).info(message);
			return;
		}
		ServletContext ctx = getServletContext();
		if (ctx != null) {
			ctx.log(message);
			return;
		}
		System.out.println(message);
	}

	protected void logError(String message, Throwable cause) {
		if (broker != null) {
			broker.getLogger(MoleculerServlet.class).error(message, cause);
			return;
		}
		ServletContext ctx = getServletContext();
		if (ctx != null) {
			ctx.log(message, cause);
			return;
		}
		System.err.println(message);
		if (cause != null) {
			cause.printStackTrace();
		}
	}

	// --- DESTROY / STOP ---

	@Override
	public void destroy() {
		super.destroy();

		// Stop cleanup timer
		if (webSocketRegistry != null) {
			try {
				webSocketRegistry.stopped();
			} catch (Throwable ignored) {
			}
			webSocketRegistry = null;
		}

		// Stop Spring Context
		ConfigurableApplicationContext ctx = context.getAndSet(null);
		if (ctx != null) {
			try {
				ctx.stop();
			} catch (Throwable ignored) {
			}
		}

		// Stop broker (if required)
		if (broker != null) {
			Tree info = broker.getConfig().getServiceRegistry().getDescriptor();
			Tree services = info.get("services");
			if (services != null && !services.isNull() && services.size() > 0) {
				broker.stop();
			}
		}
	}

	// --- GETTERS ---

	public ApiGateway getGateway() {
		return gateway;
	}

	public ServiceBroker getBroker() {
		return broker;
	}

	// --- SETTERS (FOR TESTING) ---

	public void setWorkingMode(ServiceMode serviceMode) {
		this.serviceMode = serviceMode;
	}

}