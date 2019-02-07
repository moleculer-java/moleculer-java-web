package services.moleculer.web.servlet;

import java.io.File;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;

import junit.framework.TestCase;
import services.moleculer.ServiceBroker;
import services.moleculer.monitor.ConstantMonitor;
import services.moleculer.web.ApiGateway;
import services.moleculer.web.servlet.NonBlockingMoleculerServlet;

public class ServletConnectorTest extends TestCase {

	// --- VARIABLES ---

	private ServiceBroker br;
	private ApiGateway ag;
	private NonBlockingMoleculerServlet sc;
	private CloseableHttpAsyncClient cl;
	private Server server;
	
	// --- TEST METHODS ---

	@Test
	public void testGateway() throws Exception {

		//HttpGet req = new HttpGet("http://localhost:3000");
		HttpPost req = new HttpPost("http://localhost:3000");

		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
	    builder.addTextBody("username", "John");
	    builder.addTextBody("password", "pass");
	    builder.addBinaryBody("file", new File("c:/temp/script.txt"),
	      ContentType.TEXT_PLAIN, "test.txt");
	 
	    HttpEntity multipart = builder.build();
	    req.setEntity(multipart);
		
	    HttpResponse rsp = cl.execute(req, null).get();
	    
	    System.out.println(rsp);
	}

	// --- SET UP ---

	@Override
	protected void setUp() throws Exception {
		br = ServiceBroker.builder().monitor(new ConstantMonitor()).build();
		ag = new ApiGateway();
		br.createService(ag);
		br.start();
		
		server = new Server();
		ServerConnector pContext = new ServerConnector(server);
		pContext.setHost("127.0.0.1");
		pContext.setPort(3000);
		ServletContextHandler publicContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
		publicContext.setContextPath("/");
		sc = new NonBlockingMoleculerServlet();
		sc.gateway = ag;
		sc.broker = br;
		ServletHolder sh = new ServletHolder(sc);
		sh.setInitParameter("moleculer.config", "/services/moleculer/web/moleculer.config.xml");
		publicContext.addServlet(sh, "/*");
		HandlerCollection collection = new HandlerCollection();
		collection.addHandler(publicContext);
		server.setHandler(collection);
		server.addConnector(pContext);
		server.start();
		
		cl = HttpAsyncClients.createDefault();
		cl.start();
	}

	// --- TEAR DOWN ---

	@Override
	protected void tearDown() throws Exception {
		if (server != null) {
			server.stop();
		}
		if (cl != null) {
			cl.close();
		}
	}

}
