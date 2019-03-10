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
package services.moleculer.web.servlet;

import static services.moleculer.web.common.GatewayUtils.getService;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import services.moleculer.ServiceBroker;
import services.moleculer.web.ApiGateway;

public abstract class AbstractMoleculerServlet extends HttpServlet {

	// --- UID ---

	private static final long serialVersionUID = -1038240217177335483L;

	// --- GATEWAY SERVICE ---
	
	protected ApiGateway gateway;

	// --- SERVICE BROKER'S SPRING CONTEXT ---

	protected AbstractApplicationContext ctx;

	// --- MOLECULER COMPONENTS ---

	protected ServiceBroker broker;
	protected ScheduledExecutorService scheduler;

	// --- INTERNAL VARIABLES ---

	protected int tries;
	
	// --- INIT / START ---
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		// Start ServiceBroker
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

		// Get ServiceBroker from Spring
		broker = ctx.getBean(ServiceBroker.class);

		// Find ApiGateway
		gateway = getService(broker, ApiGateway.class);
		if (gateway == null) {
			scheduler = broker.getConfig().getScheduler();
			scheduler.execute(this::initApiGateway);
		} else {
			getServletContext().log("ApiGateway connected to Servlet instance.");
		}
	}
	
	// --- INIT GATEWAY ---
	
	protected void initApiGateway() {
		gateway = getService(broker, ApiGateway.class);
		if (gateway == null) {
			tries++;
			if (tries < 50) {
				scheduler.schedule(this::initApiGateway, 200, TimeUnit.MILLISECONDS);
			} else {
				getServletContext().log("ApiGateway Service not defined!");
			}
		} else {
			getServletContext().log("ApiGateway connected to Servlet instance.");
		}
	}
	
	// --- DESTROY / STOP ---
	
	@Override
	public void destroy() {
		super.destroy();
		gateway = null;

		// Stop ServiceBroker instance
		if (broker != null) {
			broker.stop();
			broker = null;
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