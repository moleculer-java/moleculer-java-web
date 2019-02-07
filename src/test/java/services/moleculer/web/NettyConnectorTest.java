package services.moleculer.web;

import java.io.File;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.junit.Test;

import junit.framework.TestCase;
import services.moleculer.ServiceBroker;
import services.moleculer.monitor.ConstantMonitor;
import services.moleculer.web.netty.NettyServer;

public class NettyConnectorTest extends TestCase {

	// --- VARIABLES ---
	
	private ServiceBroker br;
	private ApiGateway ag;
	private NettyServer nc;
	private CloseableHttpAsyncClient cl;
	
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

		nc = new NettyServer();
		br.createService(nc);

		br.start();
		
		cl = HttpAsyncClients.createDefault();
		cl.start();
	}

	// --- TEAR DOWN ---
	
	@Override
	protected void tearDown() throws Exception {
		if (br != null) {
			br.stop();
		}
		if (cl != null) {
			cl.close();
		}
	}
	
}
