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
package services.moleculer.web;

import java.math.BigInteger;

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
import services.moleculer.service.Service;
import services.moleculer.web.middleware.CorsHeaders;
import services.moleculer.web.middleware.Favicon;
import services.moleculer.web.middleware.RateLimiter;
import services.moleculer.web.middleware.RequestLogger;
import services.moleculer.web.middleware.ServeStatic;
import services.moleculer.web.middleware.SessionCookie;
import services.moleculer.web.netty.NettyServer;
import services.moleculer.web.router.Alias;
import services.moleculer.web.router.MappingPolicy;
import services.moleculer.web.router.Route;
import services.moleculer.web.template.AbstractTemplateEngine;
import services.moleculer.web.template.FreeMarkerEngine;

public class Sample {

	public static void main(String[] args) {
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

			// AbstractTemplateEngine te = new JadeEngine();
			// te.setDefaultExtension("jade");

			AbstractTemplateEngine te = new FreeMarkerEngine();
			te.setDefaultExtension("freemarker");

			// AbstractTemplateEngine te = new MustacheEngine();
			// te.setDefaultExtension("mustache");

			// AbstractTemplateEngine te = new PebbleEngine();
			// te.setDefaultExtension("pebble");

			// AbstractTemplateEngine te = new ThymeleafEngine();
			// te.setDefaultExtension("thymeleaf");

			// AbstractTemplateEngine te = new DataTreeEngine();
			// te.setDefaultExtension("datatree");
			
			te.setReloadable(false);
			te.setTemplatePath("templates");			
			gateway.setTemplateEngine(te);

			// http://localhost:3000/math/add?a=5&b=7

			String path = "/math";
			MappingPolicy policy = MappingPolicy.ALL;
			CallOptions.Options opts = CallOptions.retryCount(3);
			String[] whitelist = {};
			Alias[] aliases = new Alias[2];
			aliases[0] = new Alias(Alias.ALL, "/add", "math.add");
			aliases[1] = new Alias(Alias.ALL, "/html", "math.html");
			
			Route r = new Route(broker, path, policy, opts, whitelist, aliases, te);

			r.use(new CorsHeaders());
			RateLimiter rl = new RateLimiter(10, false);
			rl.setHeaders(false);
			r.use(rl);
			gateway.setRoutes(new Route[] { r });

			gateway.use(new ServeStatic("/pages", "c:/Program Files/apache-cassandra-3.11.0/doc/html/"));
			gateway.use(new Favicon());
			gateway.use(new SessionCookie());
			gateway.use(new RequestLogger());
			// gateway.use(new XSRFToken());
			// gateway.use(new ResponseDeflater());
			// gateway.use(new BasicAuthenticator("user", "password"));

			broker.createService(new Service("math") {

				@Cache(keys = { "a", "b" }, ttl = 30)
				public Action add = ctx -> {

					int c = ctx.params.get("a", 0) + ctx.params.get("b", 0);
					ctx.params.put("c", c);
					return ctx.params;

				};

				@Subscribe("foo.*")
				public Listener listener = payload -> {
					System.out.println("Received: " + payload);
				};

				@SuppressWarnings("unused")
				public Action html = ctx -> {
					
					Tree rsp = new Tree();
					rsp.put("a", 1);
					rsp.put("b", true);
					rsp.put("c", "xyz");
					rsp.put("d.e", new BigInteger("3210"));
					
					Tree table = rsp.putList("table");
					for (int i = 0; i < 10; i++) {
						Tree row = table.addMap();
						row.put("first", "12345");
						row.put("second", i % 2 == 0);
						row.put("third", i);
					}
					
					rsp.getMeta().put("$template", "test");
					
					return rsp;

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

					// TODO Middleware not started (broker = null)
					// broker.getLogger().info("Middleware not installed to " +
					// config.toString(false));
					return null;
				}

			});

		} catch (Exception cause) {
			cause.printStackTrace();
		}
	}

}