# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`moleculer-java-web` is the **API Gateway** for [moleculer-java](https://github.com/moleculer-java/moleculer-java).
It is a Moleculer `Service` (`ApiGateway`) that exposes other Moleculer services over HTTP/REST and
WebSocket. It is server-independent: the same gateway runs behind a standalone **Netty** server or
inside any **Jakarta servlet container** (Jetty 12+, Tomcat 10+, WildFly, …). Published to Maven Central as
`com.github.berkesa:moleculer-java-web`; the Maven build names the JAR after the artifactId
(`moleculer-java-web-<version>.jar`).

All data — request params, action config, responses — flows as `io.datatree.Tree` (the JSON-like
structure from the datatree library that the whole Moleculer ecosystem uses).

## Build / test / run

Maven build (one `pom.xml`); bytecode target **Java 17** (`<maven.compiler.release>17</maven.compiler.release>`),
build JDK 17+ (JDK 25 in use), minimum consumer runtime **JDK 17** (Spring 6 direct dep),
compiled with `javac`. Version is **2.0.0** (use `2.0.0-SNAPSHOT` while developing).

```bash
mvn clean verify             # compile + run all tests
mvn clean install            # also install moleculer-java-web-<version>.jar into the local ~/.m2
mvn -Prelease clean deploy   # sources + javadoc + GPG + Central Portal publish (release profile)

# run a single test class
mvn test -Dtest=NettyTest
# run a single test method
mvn test -Dtest='NettyTest#testMiddlewares'
```

`Sample.java` (in `src/test/java`) has a `main()` showing the minimal standalone setup; run it (or the
`.vscode/launch.json` "Sample" config) to start a local Netty gateway on port 8080.

### Build gotchas

- **Resource quirk:** only `**/*.ico` files under `src/main/java`
  are bundled into the jar (used by the `Favicon` middleware) — see `<build><resources>` in `pom.xml`.
  Test fixtures (templates, html, message bundles, `moleculer.config.xml`) live under `src/test/java` and
  are picked up via `<testResources>`.
- **Connector / servlet / WebSocket API deps are `provided`; template engines + the multipart parser are
  `<optional>`** — a consumer pulls only the connector and template engine it actually uses. Netty +
  `moleculer-java` are normal compile deps.
- **Tests bind `127.0.0.1:3000`** (Netty + embedded Jetty 12) and run sequentially; `AbstractTemplateTest`
  waits for the port to be free before each bind. They run fully offline (no external broker).

## Architecture: the request-processing chain

The core abstraction is `RequestProcessor.service(WebRequest req, WebResponse rsp)`. `WebRequest`/`WebResponse`
hide whether the request came from Netty or a servlet, so nothing below the connector layer knows about
either. The flow:

1. **Connector** adapts the native request to `WebRequest`/`WebResponse` and calls `ApiGateway.service(...)`:
   - **Netty** — `NettyServer` builds the channel pipeline; `MoleculerHandler` wraps requests as
     `NettyWebRequest`/`NettyWebResponse`. Standalone, non-blocking, supports SSL (JDK or OpenSSL) and WebSocket.
     **Slowloris hardening:** `NettyServer.setReadTimeout(seconds)` (off by default, `0`) inserts an
     `IdleStateHandler` so connections that stall mid-request are closed; `MoleculerHandler`'s
     `userEventTriggered`/`channelInactive`/`exceptionCaught` release the half-open `req.stream`, and the idle
     handler is removed from the pipeline on a successful WebSocket upgrade (long-lived sockets are managed by
     `NettyWebSocketRegistry` instead). See `NettySlowRequestTest`.
   - **Servlet** — `MoleculerServlet` boots a Spring 6 context (Spring Boot via `moleculer.application`, or XML
     via `moleculer.config`), finds the `ApiGateway`, and auto-detects **blocking vs non-blocking** mode
     (`AsyncService` when async I/O is available — detected via `Class.forName("jakarta.servlet.ReadListener")`
     — else `BlockingService`; falls back to blocking on `IllegalStateException`, and forces blocking on WebLogic).
     The servlet/WebSocket layer is **Jakarta** (`jakarta.servlet.*` / `jakarta.websocket.*`); the J2EE
     WebSocket connector (`ServletWebSocketRegistry` + the `websocket` package) targets the JSR-356 API
     supplied by a Jakarta container (e.g. Jetty 12 ee10). Request read timeouts are owned by the container;
     for non-blocking mode the `moleculer.async.timeout` init-param (ms, `0` = container default) caps the
     async context (`ServiceMode.setAsyncTimeout` → `AsyncContext.setTimeout`), and `NonBlockingWebRequest`
     registers an `AsyncListener` that aborts the body stream on timeout/error.

2. **`ApiGateway.service(...)`** resolves the request to a `Mapping`:
   - looks in the **static mapping cache** (exact `METHOD path` key, e.g. `GET /user`), then the
     **dynamic mapping cache** (parameterized paths). Both caches are guarded by a `ReentrantReadWriteLock`
     and bounded by `cachedRoutes` (default 2048).
   - on a miss, walks the `Route[]`, then `lastRoute` (which runs `lastMiddleware`, default `NotFound` = 404),
     and caches the resulting mapping.

3. **`Route`** turns an incoming `(method, path)` into a `Mapping` via `findMapping(...)`, using, in order:
   **aliases** → **whitelist patterns** → **mapping policy**. A route carries its own middlewares, optional
   template engine, `beforeCall`/`afterCall` hooks, and executor.

4. **`Mapping`** builds and owns the middleware chain. The chain is constructed **inside-out**: `ActionInvoker`
   (the terminal processor that actually calls the Moleculer action and serializes the result) is the innermost
   `parent`; each `HttpMiddleware.install(next, config)` wraps the current head and becomes the new head
   (`lastProcessor`). A request enters at the outermost middleware and unwinds toward `ActionInvoker`.

### Routing concepts (in `services.moleculer.web.router`)

- **Alias** — explicit `httpMethod + pathPattern -> actionName`. `ALL` matches any method. A `REST` alias
  auto-expands into 5 CRUD aliases: `GET list`, `GET /:id get`, `POST create`, `PUT /:id update`,
  `DELETE /:id remove`.
- **Whitelist + `MappingPolicy`** — `RESTRICT` (default) exposes only whitelisted name patterns; `ALL` exposes
  everything. When matched by whitelist/policy, the path is converted to an action name: `/` → `.`, `~` → `$`.
- **Path patterns** — static (`/user`), wildcard (`/files*`), or parameterized (`/user/:id`, compiled to a
  regex with a per-mapping matcher cache). Static mappings cache far more cheaply than dynamic ones.
- **`@HttpAlias`** annotation on a service's action field — the gateway auto-deploys these by subscribing to
  the `$services.changed` event (`autoDeployListener` in `ApiGateway`), so annotated actions appear as routes
  without manual `addRoute`/`addAlias` calls.

### Middlewares (`services.moleculer.web.middleware`)

Every middleware extends `HttpMiddleware` (which is itself a Moleculer `Service`, so it has lifecycle
`started`/`stopped`) and implements `install(next, config)` returning a `RequestProcessor` that delegates to
`next`. Added globally via `ApiGateway.use(...)` or per-route via `Route.use(...)`. Notable ones: `CorsHeaders`,
`BasicAuthenticator`, `RateLimiter` (pluggable `RatingStore`), `ServeStatic`, `Favicon`, `SessionHandler`
(pluggable `SessionStore`), `ResponseDeflater` (gzip), `Redirector`, `ErrorPage`, `NotFound`, `IpFilter`,
`HostNameFilter`, `XSRFToken`, `ResponseTime`, `ResponseTimeout`, `RequestLogger`, `TopLevelCache`.

### Template engines (`services.moleculer.web.template`)

Pluggable server-side HTML rendering, all extending `AbstractTemplateEngine`: DataTree, FreeMarker,
Mustache, Thymeleaf, Pebble, Handlebars, Velocity. Set globally on the gateway (`setTemplateEngine`) or per route. Each engine's
library is an `<optional>` dependency in `pom.xml`, so a consumer only needs the one it actually uses.

### WebSockets

`WebSocketRegistry` (Netty: `NettyWebSocketRegistry`; Servlet: `ServletWebSocketRegistry`) tracks client
connections per path. **Server-to-client push is event-driven**: broadcast the Moleculer event `websocket.send`
with `{ path, data }` and the gateway's `webSocketListener` forwards it to matching sockets. `WebSocketFilter`
gates which clients may connect.

### Customization hooks

`CallProcessor` `beforeCall`/`afterCall` (mutate the request `Tree`/response around the action call) and a
custom `ExecutorService` can be set on the `ApiGateway` (applied to all routes) or per `Route`.

## Wiring it together

A gateway only works when deployed into a `ServiceBroker` alongside a connector service. Minimal Netty setup:

```java
new ServiceBroker()
    .createService(new NettyServer(8080))   // connector
    .createService(new ApiGateway("**"))    // gateway; "**" whitelists all services
    .createService(new Service("math") { Action add = ctx ->
        ctx.params.get("a", 0) + ctx.params.get("b", 0); })
    .start();
// GET http://localhost:8080/math/add?a=3&b=6
```

Tests are **JUnit 5 (Jupiter)**. In `NettyTest`, `BlockingServletTest`, `NonBlockingServletTest` (and the
WebSocket tests `NettyWebSocketTest` / `JettyWebSocketTest`), the broker is built with a `ConstantMonitor`,
the connector and `ApiGateway` are created on it. The servlet tests run on an **embedded Jetty 12 (ee10)**
container. `AbstractTemplateTest` is the shared base: subclasses implement `startServer()`/`stopServer()`
(so the connector is built before the routes are installed), and it drives HTTP requests with the
**httpclient5** async client (`org.apache.hc.client5`). The rate-limiter loop uses a plain `HttpURLConnection`
to keep its rapid sequential requests inside the 1-second window. Subclass `AbstractTemplateTest` to reuse
its assertions against a different connector/mode.
