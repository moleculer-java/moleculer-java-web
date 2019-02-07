/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2017 Andras Berkes [andras.berkes@programmer.net]<br>
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

import java.util.concurrent.TimeUnit;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.cacher.Cache;
import services.moleculer.config.ServiceBrokerConfig;
import services.moleculer.context.CallOptions;
import services.moleculer.context.Context;
import services.moleculer.eventbus.Listener;
import services.moleculer.eventbus.Subscribe;
import services.moleculer.service.Action;
import services.moleculer.service.Middleware;
import services.moleculer.service.Name;
import services.moleculer.service.Service;
import services.moleculer.web.middleware.CorsHeaders;
import services.moleculer.web.middleware.Favicon;
import services.moleculer.web.middleware.RateLimiter;
import services.moleculer.web.middleware.RequestLogger;
import services.moleculer.web.middleware.ServeStatic;
import services.moleculer.web.middleware.SessionCookie;
import services.moleculer.web.middleware.limiter.RateLimit;
import services.moleculer.web.netty.NettyServer;
import services.moleculer.web.router.Alias;
import services.moleculer.web.router.MappingPolicy;
import services.moleculer.web.router.Route;

public class Sample {

	public static void main(String[] args) {
		System.out.println("START");
		try {
			ServiceBroker b = new ServiceBroker();
			b.start();
			
			NettyServer c = new NettyServer();
			c.started(b);
			
		} catch (Exception cause) {
			cause.printStackTrace();
		}
	}
	
	public static void main2(String[] args) {
		System.out.println("START");
		try {
			ServiceBrokerConfig cfg = new ServiceBrokerConfig();

			// RedisTransporter t = new RedisTransporter();
			// t.setDebug(false);
			// cfg.setTransporter(t);

			ServiceBroker broker = new ServiceBroker(cfg);

			NettyServer server = new NettyServer();
			// gateway.setUseSSL(false);
			// gateway.setPort(3000);
			// gateway.setKeyStoreFilePath("/temp/test.jks");
			// gateway.setKeyStorePassword("test");
			broker.createService(server);

			ApiGateway gateway = new ApiGateway();
			broker.createService(gateway);
			
			// http://localhost:3000/math/add?a=5&b=6

			String path = "/math";
			MappingPolicy policy = MappingPolicy.ALL;
			CallOptions.Options opts = CallOptions.retryCount(3);
			String[] whitelist = {};
			Alias[] aliases = new Alias[1];
			aliases[0] = new Alias(Alias.ALL, "/add", "math.add");
			
			Route r = new Route(broker, path, policy, opts, whitelist, aliases);
			r.use(new CorsHeaders());
			RateLimiter rl = new RateLimiter(10, false);
			rl.setHeaders(false);
			r.use(rl);
			gateway.setRoutes(new Route[]{r});

			gateway.use(new ServeStatic("/pages", "c:/temp"));
			gateway.use(new Favicon());
			gateway.use(new SessionCookie());
			gateway.use(new RequestLogger());

			broker.createService(new Service("math") {

				@Name("add")
				@RateLimit(value = 10, window = 1, unit = TimeUnit.MINUTES)
				@Cache(keys = { "a", "b" }, ttl = 30)
				public Action add = ctx -> {

					//broker.getLogger().info("Call " + ctx.params);
					return ctx.params.get("a", 0) + ctx.params.get("b", 0);

				};

				@Name("test")
				public Action test = ctx -> {

					return ctx.params.get("a", 0) + ctx.params.get("b", 0);

				};

				@Subscribe("foo.*")
				public Listener listener = payload -> {
					System.out.println("Received: " + payload);
				};

			});
			broker.start();
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