[![Build Status](https://travis-ci.org/moleculer-java/moleculer-java-web.svg?branch=master)](https://travis-ci.org/moleculer-java/moleculer-java-web)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/409fc5fe713e46d5bce3fa7c7452931a)](https://www.codacy.com/app/berkesa/moleculer-java-web?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=moleculer-java/moleculer-java-web&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/moleculer-java/moleculer-java-web/branch/master/graph/badge.svg)](https://codecov.io/gh/moleculer-java/moleculer-java-web)

# Moleculer API Gateway

[Moleculer](https://moleculer-java.github.io/moleculer-java/)
API Gateway is a Service that makes other Moleculer (Java and/or NodeJS) Services available through REST.
It allows to create high-performance, non-blocking, distributed web applications.
Web request processing can be fine tuned using server-independent middlewares.
Moleculer API Gateway provides full support for high-load React, Angular or VueJS applications.

## Features

- The same code can run as a J2EE Servlet and as high-performance Netty application without any changes
- WebSocket support (same API for Netty Server and J2EE Servers)
- Can run as a non-blocking Servlet (tested on JBoss EAP, GlassFish, Tomcat, Jetty, WebLogic, WAS Liberty)
- Able to run without a Servlet Container ("high performance mini webserver" using Netty Server)
- SSL/HTTPS support
- Serving static files (HTML, CSS, JavaScript, images, videos, etc.)
- Multiple routes (eg. authenticated route for Services, not authenticated for static files)
- Global, route, alias middlewares
- Large file uploading using Moleculer Streams
- Alias names (eg. map "/foo/:param1/:param2" to "service.action")
- Whitelist (eg. "service.*")
- CORS headers
- ETags
- Rate limiter
- Before & after call hooks
- Authorization
- Integrated with server-side template engines (FreeMarker, Jade, Pebble, Thymeleaf, Mustache, Velocity)
- And much more (favicon, custom error messages, session cookies, etc.)

## Download

**Maven**

```xml
<dependencies>
    <dependency>
        <groupId>com.github.berkesa</groupId>
        <artifactId>moleculer-java-web</artifactId>
        <version>1.2.6</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

**Gradle**

```gradle
dependencies {
    compile group: 'com.github.berkesa', name: 'moleculer-java-web', version: '1.2.6' 
}
```

## Short Example

The simplest way to create a REST service using Moleculer is the following:

```java
new ServiceBroker()
    .createService(new NettyServer(8080))
    .createService(new ApiGateway("*"))
    .createService(new Service("math") {
       Action add = ctx -> {
         return ctx.params.get("a", 0) +
                ctx.params.get("b", 0);
         };
       }).start();
```
After starting the program, enter the following URL into your browser:  
`http://localhost:8080/math/add?a=3&b=6`

The response will be "9". The above service can also be invoked using a POST method.  
To do this, submit the `{"a":3,"b":5}` JSON (as POST body) to this URL:  
`http://localhost:8080/math/add`

## Detailed Example

[This demo project](https://moleculer-java.github.io/moleculer-spring-boot-demo/)
demonstrating some of the capabilities of APIGateway.  
The project can be imported into the Eclipse IDE. The brief examples illustrate the following:

- Integration of Moleculer API into the Spring Boot Framework
- Configuring HTTP Routes and Middlewares
- Creating non-blocking Moleculer Services
- Publishing and invoking Moleculer Services as REST Services
- Generating HTML pages in multiple languages using Template Engines
- Using WebSockets (sending real-time server-side events to browsers)
- Using file upload and download
- Video streaming and server-side image generation
- Creating a WAR from the finished project (Servlet-based runtime)
- Run code without any changes in "standalone mode" (Netty-based runtime)

# License
This project is available under the [MIT license](https://tldrlegal.com/license/mit-license).
