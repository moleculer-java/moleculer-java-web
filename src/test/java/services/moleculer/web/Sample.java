package services.moleculer.web;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.cacher.Cache;
import services.moleculer.config.ServiceBrokerConfig;
import services.moleculer.context.CallingOptions;
import services.moleculer.context.Context;
import services.moleculer.eventbus.Listener;
import services.moleculer.eventbus.Subscribe;
import services.moleculer.service.Action;
import services.moleculer.service.Middleware;
import services.moleculer.service.Name;
import services.moleculer.service.Service;
import services.moleculer.service.Version;
import services.moleculer.transporter.RedisTransporter;
import services.moleculer.web.middleware.CorsHeaders;
import services.moleculer.web.middleware.RateLimiter;
import services.moleculer.web.middleware.RequestLogger;
import services.moleculer.web.middleware.ServeStatic;
import services.moleculer.web.middleware.SessionCookie;
import services.moleculer.web.middleware.limiter.RateLimit;
import services.moleculer.web.router.Alias;
import services.moleculer.web.router.MappingPolicy;
import services.moleculer.web.router.Route;

public class Sample {

	public static void main(String[] args) {
		System.out.println("START");
		try {
			ServiceBrokerConfig cfg = new ServiceBrokerConfig();
			
			RedisTransporter t = new RedisTransporter();
			t.setDebug(false);
			// cfg.setTransporter(t);
						
			NettyGateway gateway = new NettyGateway();
			gateway.setUseSSL(false);
			gateway.setKeyStoreFilePath("/temp/test.jks");
			gateway.setKeyStorePassword("test");
			
			ServiceBroker broker = new ServiceBroker(cfg);

			broker.createService("api-gw", gateway);
			
			String path = "/math";
			MappingPolicy policy = MappingPolicy.ALL;
			CallingOptions.Options opts = CallingOptions.retryCount(3);
			String[] whitelist = {};
			Alias[] aliases = new Alias[1];
			aliases[0] = new Alias(Alias.ALL, "/add", "math.add");
			
			Route r = new Route(gateway, path, policy, opts, whitelist, aliases);
			r.use(new CorsHeaders());
			r.use(new RateLimiter());
			gateway.setRoutes(new Route[]{r});

			gateway.use(new ServeStatic("/pages", "c:/temp"));
			gateway.use(new SessionCookie());
			gateway.use(new RequestLogger());
		
			broker.createService(new Service("math") {

				@Name("add")
				@RateLimit(20)
				@Cache(keys = { "a", "b" }, ttl = 30)
				public Action add = ctx -> {

					//broker.getLogger().info("Call " + ctx.params);
					return ctx.params.get("a", 0) + ctx.params.get("b", 0);

				};

				@Name("test")
				@Version("1")
				public Action test = ctx -> {

					return ctx.params.get("a", 0) + ctx.params.get("b", 0);

				};

				@Subscribe("foo.*")
				public Listener listener = payload -> {
					System.out.println("Received: " + payload);
				};

			});			
			broker.start();
			broker.repl();
			broker.use(new Middleware() {

				@Override
				public Action install(Action action, Tree config) {
					int version = config.get("version", 0);
					if (version > 0) {
						broker.getLogger().info("Middleware installed to " + config.toString(false));
						return new Action() {

							@Override
							public Object handler(Context ctx) throws Exception {
								Object original = action.handler(ctx);
								Object replaced = System.currentTimeMillis();
								broker.getLogger()
										.info("Middleware invoked! Replacing " + original + " to " + replaced);
								return replaced;
							}

						};
					}
					broker.getLogger().info("Middleware not installed to " + config.toString(false));
					return null;
				}

			});

		} catch (Exception cause) {
			cause.printStackTrace();
		}
	}

}
