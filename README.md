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
    <version>2.0.0</version>
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

## Requirements

Java 21 or newer.

## License

Moleculer API Gateway is available under the [MIT license](https://tldrlegal.com/license/mit-license).
