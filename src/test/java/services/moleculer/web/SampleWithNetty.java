package services.moleculer.web;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.config.ServiceBrokerConfig;
import services.moleculer.service.Action;
import services.moleculer.service.Service;
import services.moleculer.web.netty.NettyServer;
import services.moleculer.web.router.RestRoute;
import services.moleculer.web.router.StaticRoute;

public class SampleWithNetty {

	public static void main(String[] args) {
		System.out.println("START");
		try {

			// Create service borker
			ServiceBrokerConfig cfg = new ServiceBrokerConfig();
			// cfg.setTransporter(new NatsTransporter());
			// ...
			ServiceBroker broker = new ServiceBroker(cfg);

			// Open local REPL console
			broker.repl();

			// Create standalone Netty server
			NettyServer server = new NettyServer();
			broker.createService(server);

			// APIGateway is required
			ApiGateway gateway = new ApiGateway();
			gateway.setDebug(true);
			broker.createService(gateway);

			// REST services
			RestRoute rest = new RestRoute();
			rest.addAlias("/test", "test.send");
			gateway.addRoute(rest);

			// Static web content
			StaticRoute www = new StaticRoute("/templates");
			www.setEnableReloading(true);
			gateway.addRoute(www);

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
			broker.start();

		} catch (Exception cause) {
			cause.printStackTrace();
		}
	}

}
