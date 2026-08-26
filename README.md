# Moleculer API Gateway

The API Gateway for [Moleculer for Java](https://moleculer-java.github.io/site/). It is a
Moleculer `Service` that exposes your other services over **HTTP/REST and WebSocket**, so you
can build high-performance, non-blocking, distributed web applications. The gateway is
**server-independent** — the same code runs behind a standalone **Netty** server or inside any
**Jakarta servlet container** (Jetty 12+, Tomcat 10+, WildFly, …) — and request handling is
fully customizable through server-independent middlewares. It provides everything a modern
**React**, **Angular**, or **Vue** front-end needs from its backend.

## Documentation

[![Documentation](https://raw.githubusercontent.com/moleculer-java/site/master/docs/docs-button.png)](https://moleculer-java.github.io/site/moleculer-web.html)

## Download

```xml
<dependency>
    <groupId>com.github.berkesa</groupId>
    <artifactId>moleculer-java-web</artifactId>
    <version>2.1.0</version>
</dependency>
```

## Quick start

Deploy a Netty connector and an `ApiGateway` into a broker; `"**"` publishes every service:

```java
new ServiceBroker()
    .createService(new NettyServer(8080))   // HTTP connector
    .createService(new ApiGateway("**"))    // gateway; "**" exposes all services
    .createService(new Service("math") {
        public Action add = ctx -> ctx.params.get("a", 0) + ctx.params.get("b", 0);
    })
    .start();

// GET http://localhost:8080/math/add?a=3&b=6   ->   9
```

## Security: slow-request (Slowloris) hardening

A client can open many connections and send only **part** of each request (half a header
block or a body shorter than its `Content-Length`), then go silent. Without a read timeout
those connections stay open waiting for data that never arrives, slowly exhausting file
descriptors and memory. To defend against this:

- **Standalone Netty** — set a **read timeout** (seconds) on the connector. When a client
  stays silent that long while a request is still being read, the connection is closed.
  Disabled by default (`0`); enable it on Internet-facing deployments:

  ```java
  NettyServer netty = new NettyServer(8080);
  netty.setReadTimeout(30); // close half-open requests after 30 s of silence
  ```

  Established WebSockets are exempt (they are intentionally long-lived). Note: the timeout
  fires when the client *stops* sending — an attacker dripping bytes just under the interval,
  or very long silent downloads/SSE over raw Netty, need a larger value or a reverse proxy.

- **Servlet containers** — the container governs request read timeouts. Configure them
  (e.g. Tomcat `connectionTimeout`, Jetty connector idle timeout). For the **non-blocking**
  servlet mode you can additionally cap the async request via the
  `moleculer.async.timeout` init parameter (milliseconds; `0` = use the container default):

  ```xml
  <init-param>
      <param-name>moleculer.async.timeout</param-name>
      <param-value>30000</param-value>
  </init-param>
  ```

- **Reverse proxy** — running behind nginx / HAProxy / a cloud load balancer (which buffer
  and time out incomplete requests) neutralizes this attack class and is recommended for
  production regardless of the connector.

## Requirements

Java 17 or newer.

## License

Moleculer API Gateway is available under the [MIT license](https://tldrlegal.com/license/mit-license).
