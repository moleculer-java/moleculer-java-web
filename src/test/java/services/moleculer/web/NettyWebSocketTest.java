package services.moleculer.web;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.Test;

import io.datatree.Tree;
import junit.framework.TestCase;
import services.moleculer.ServiceBroker;
import services.moleculer.config.ServiceBrokerConfig;
import services.moleculer.service.Action;
import services.moleculer.service.Service;
import services.moleculer.web.netty.NettyServer;
import services.moleculer.web.router.Route;

/**
 * "STANDALONE" server mode (without J2EE server / servlet container). Using Netty.
 */
public class NettyWebSocketTest extends TestCase {

	protected NettyServer server;
	protected ServiceBroker broker;
	protected ApiGateway gateway;
	protected WebSocketClient client;

	@Override
	protected void setUp() throws Exception {

		// --- SERVICE BROKER ---
		
		ServiceBrokerConfig cfg = new ServiceBrokerConfig();
		// cfg.setTransporter(new NatsTransporter());
		// ...
		broker = new ServiceBroker(cfg);

		// Open local REPL console
		// broker.repl();

		// --- NETTY WEBSERVER ---
		
		// Create standalone Netty server
		NettyServer server = new NettyServer();
		broker.createService(server);

		// --- ADD A ROUTE TO REST SERVICE ---
		
		Route route = new Route();
		route.addAlias("/test", "test.send");

		gateway = new ApiGateway();
		gateway.setDebug(true);
		gateway.addRoute(route);
		
		broker.createService(gateway);
		
		// --- TEST MOLCEULER SERVICE ---
			
		// Moleculer Service, which sends a websocket message
		broker.createService(new Service("test") {

			// The "test.send" service ("action")
			@SuppressWarnings("unused")
			Action send = ctx -> {

				Tree packet = new Tree();
				packet.put("path", "/ws/test");
				packet.put("data", 123);

				// Send a websocket packet to HTML pages
				ctx.broadcast("websocket.send", packet);

				return "Data submitted!";
			};

		});
		broker.start();

		// --- TEST CLIENT ---
		
		// Emulate web browser (see "websocket.js")
		URI uri = new URI("ws://localhost:3000/ws/test");
		client = new WebSocketClient(uri, new Draft_6455()) {
			
			@Override
			public void onMessage(String message) {
				System.out.println("MSG RECEIVED: " + message);
				msg = message;
			}

			@Override
			public void onOpen(ServerHandshake handshake) {
				System.out.println("WS OPENED");
			}

			@Override
			public void onClose(int code, String reason, boolean remote) {
				System.out.println("WS CLOSED");
			}

			@Override
			public void onError(Exception ex) {
				ex.printStackTrace();
			}

		};
		client.connect();
	}

	// ---------------- TESTS ----------------

	protected String msg;

	@Test
	public void testWS() throws Exception {
		assertNull(msg);
		Thread.sleep(1000);
		URL url = new URL("http://localhost:3000/test");
		HttpURLConnection c = (HttpURLConnection) url.openConnection();
		assertEquals(200, c.getResponseCode());		
		Thread.sleep(500);
		assertEquals("123", msg);
	}

	// ---------------- STOP ----------------

	@Override
	protected void tearDown() throws Exception {
		if (broker != null) {
			broker.stop();
		}
		if (server != null) {
			server.stopped();
		}
		if (client != null) {
			client.close();
		}
	}

}