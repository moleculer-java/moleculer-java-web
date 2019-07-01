[![Build Status](https://travis-ci.org/moleculer-java/moleculer-java-web.svg?branch=master)](https://travis-ci.org/moleculer-java/moleculer-java-web)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/409fc5fe713e46d5bce3fa7c7452931a)](https://www.codacy.com/app/berkesa/moleculer-java-web?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=moleculer-java/moleculer-java-web&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/moleculer-java/moleculer-java-web/branch/master/graph/badge.svg)](https://codecov.io/gh/moleculer-java/moleculer-java-web)

Java API gateway service for [Moleculer](https://github.com/berkesa/moleculer-java).

## Features

- Runs as J2EE Servlet or "standalone mode" with Netty
- WebSocket support (same API for Netty and J2EE Servers)
- Can run as a non-blocking Servlet (can be integrated with JBoss, Tomcat, Jetty, etc.)
- Able to run without a Servlet Container ("high performance mini webserver")
- SSL/HTTPS support
- Serving static files (HTML, CSS, JavaScript, images, etc.)
- Multiple routes (eg. authenticated route for Services, not authenticated for static files)
- Global, route, alias middlewares
- Large file uploading as a Moleculer stream
- Alias names (eg. map "/foo/:param1/:param2" to "service.action")
- Whitelist (eg. "service.*")
- CORS headers
- ETags
- Rate limiter
- Before & after call hooks
- Authorization
- And much more (favicon, custom error messages, session cookies, etc.)

## Download

**Maven**

```xml
<dependencies>
	<dependency>
		<groupId>com.github.berkesa</groupId>
		<artifactId>moleculer-java-web</artifactId>
		<version>1.0.1</version>
		<scope>runtime</scope>
	</dependency>
</dependencies>
```

**Gradle**

```gradle
dependencies {
	compile group: 'com.github.berkesa', name: 'moleculer-java-web', version: '1.0.1' 
}
```

# License
moleculer-java-web is available under the [MIT license](https://tldrlegal.com/license/mit-license).
