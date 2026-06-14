# TODO — Modernize `moleculer-java-web` to 2.0.0

> **You are the per-project Claude Code instance for `moleculer-java-web`.** Self-contained file.
> Goal: Gradle/Java 8 → **Maven + JDK 21**, upgrade deps, resolve the big **`javax`→`jakarta`**
> (servlet + Jetty WebSocket) migration, tests green on **JUnit 5**, legacy files removed, version
> **2.0.0**. This is the **API Gateway** (`ApiGateway` Moleculer service) exposing services over
> HTTP/REST + WebSocket, behind standalone **Netty** or a **servlet container**. Packages:
> `services.moleculer.web.*`. **This is the heaviest jakarta migration in the suite.**

## Coordinates & facts
- Maven: `com.github.berkesa:moleculer-java-web`, `jar`, license **MIT**.
- `name`: *Java API gateway service for Moleculer* · `inceptionYear`: 2019
- `url`: https://moleculer-java.github.io/moleculer-java-web/ · `scm`: https://github.com/moleculer-java/moleculer-java-web.git
- developer: `berkesa` / Andras Berkes / andras.berkes@programmer.net
- **Version → `2.0.0`** (old build: `version` + `jar` block → single Maven `<version>`).

## Inter-project dependencies (PIN to 2.0.0)
- `com.github.berkesa:moleculer-java:2.0.0` (was 1.2.28)
- `com.github.berkesa:moleculer-java-repl:2.0.0` (was 1.3.1) — optional console
- `com.github.berkesa:datatree-templates:2.0.0` (was 1.1.4) — the DataTree template engine option

Build order before this one: `datatree*` → `moleculer-java` → `moleculer-java-repl` →
`datatree-templates`. Ensure their `2.0.0-SNAPSHOT` are in local `~/.m2`.

## ⚠ The jakarta migration (do this carefully)
1. **Servlet:** `javax.servlet.*` → `jakarta.servlet.*`. Affects `MoleculerServlet`,
   `AsyncService`, `BlockingService` and the Servlet 3.1-async detection. Target containers are now
   jakarta (Tomcat 10+, Jetty 12, WildFly). Declare the servlet API as `provided`:
   `jakarta.servlet:jakarta.servlet-api:6.x`.
2. **Jetty 9.4 → 12 (ee10):** replace `org.eclipse.jetty.websocket:javax-websocket-server-impl:9.4.x`
   and `org.eclipse.jetty:jetty-continuation:9.4.x` (continuations were **removed** years ago) with
   the Jetty 12 jakarta WebSocket artifacts (`org.eclipse.jetty.ee10.websocket:jetty-ee10-websocket-jakarta-server:12.x`).
   Update `ServletWebSocketRegistry` to the `jakarta.websocket` API.
3. **Spring:** `spring-context` 5.3.7 → **6.2.8** (jakarta, Java 17+; lockstep with moleculer-java's
   Spring 6.2.8 / Boot 3.5.3 — note Boot 3 rejects circular bean refs by default); fix `MoleculerServlet`'s
   Spring context bootstrapping.
Netty path stays `javax`-free; the migration is concentrated in the servlet + Jetty connectors.

## Dependency actions (confirm newest at execution)
| Dependency | Current | Target |
|---|---|---|
| `org.slf4j:*` | 1.7.30 | **2.0.18** |
| `org.springframework:spring-context` | 5.3.7 | **6.2.8** ⚠ jakarta (🔒 lockstep — locked by moleculer-java) |
| `io.netty:netty-handler` + `netty-codec-http` | 4.1.65.Final | **4.2.x** (security fixes) |
| `org.java-websocket:Java-WebSocket` | 1.5.2 | **1.6.0** |
| Jetty `javax-websocket-server-impl` + `jetty-continuation` 9.4.41 | → **Jetty 12 ee10 jakarta websocket** | ⚠ |
| servlet API | (implicit javax) | **`jakarta.servlet:jakarta.servlet-api:6.x`** (`provided`) |
| `org.synchronoss.cloud:nio-multipart-parser` | 1.1.0 | abandoned — evaluate Commons FileUpload2 (jakarta) or Netty multipart |
| `com.github.berkesa:datatree-templates` | 1.1.4 | **2.0.0** |
| Template engines (optional/runtime) | | `freemarker` 2.3.31→**2.3.34**; `mustache` 0.9.10→**0.9.14**; `thymeleaf` 3.0.12→**3.1.5.RELEASE** ⚠ jakarta; `pebble` (`com.mitchellbosecke`→**`io.pebbletemplates`**) 2.4.0→**3.2.4** ⚠ jakarta — 🔒 these four lockstep with datatree-templates; `handlebars` 4.2.0→**4.4.x**; `velocity` 2.3→**2.4** |
| `de.neuland-bfi:jade4j` 1.3.2 | **DROP** (dead) | remove the Jade template engine option |
| test: `httpasyncclient` 4.1.4 + `httpmime` 4.5.13 | → **`org.apache.httpcomponents.client5:httpclient5:5.x`** (test scope) | the test HTTP driver |
| `com.openpojo:openpojo` | 0.8.10 | **0.9.1** (test; 🔒 lockstep — locked by datatree-templates) |
| Eclipse `ecj` 4.4.2 | — | **remove** |

Mark the template engines, the Jetty/servlet connectors, and the multipart parser
**`<optional>true</optional>`** (callers pull only the connector/engine they use). Netty +
`moleculer-java` are normal `compile` deps.

## Steps
1. **`pom.xml`** (metadata + MIT + `release=21`). Import `jackson-bom` if Jackson appears
   transitively you need to pin. Apply the dependency table. **Reproduce the resource quirk:** the
   Gradle build bundled only `**/*.ico` from `src/main/java` (for the `Favicon` middleware) and put
   test resources under `src/test/java` — set `<build><resources>` to include `src/main/java` with
   `<includes><include>**/*.ico</include></includes>`, and `<testResources>` for `src/test/java`
   non-java files. Build plugins: compiler **3.15.0**, surefire **3.5.4** (lockstep with the rest of
   the workspace), (release profile) sources/javadoc/gpg + central-publishing 0.9.0.
2. **Remove ECJ** → javac.
3. **Do the jakarta migration** (servlet + Jetty 12 + Spring 6) above; then Netty 4.2, Java-WebSocket
   1.6, template-engine upgrades, drop Jade, multipart replacement.
4. **Tests → JUnit 5.** Suites: `NettyTest`, `BlockingServletTest`, `NonBlockingServletTest`,
   `JettyWebSocketTest`, and `AbstractTemplateTest` (the shared HTTP-driving base — migrate it to
   **httpclient5**). The broker is built with a `ConstantMonitor`; connector + `ApiGateway` created
   on it. Keep `Sample.java` (`main()` → Netty gateway on 8080) compiling. `mvn test` green offline
   (ensure ports free or tag/disable the ones that bind).
5. **Preserve behavior:** the `RequestProcessor.service(...)` chain, static+dynamic mapping caches
   (`cachedRoutes`), `Route.findMapping` order (aliases→whitelist→policy), inside-out middleware
   chain ending at `ActionInvoker`, `@HttpAlias` auto-deploy via `$services.changed`, REST alias
   expansion, and the event-driven `websocket.send` push.
6. **Cleanup — delete:** `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/`,
   `.gradle/`, `.codacy.yaml`, `old-travis-config.yml`, `.classpath`, `.project`, `.settings/`.
7. **VSCode + .gitignore.** Add `.vscode/launch.json` for `services.moleculer.web.Sample`.
8. **Build & install:** `mvn clean install`, then `mvn clean verify`.
9. **Update `CLAUDE.md`:** Maven commands; the servlet/Jetty jakarta move; dropped Jade; httpclient5
   test driver; version `2.0.0` (single Maven `<version>`).

## Definition of done
- `mvn clean verify` green on JDK 21; `javax`→`jakarta` (servlet + Jetty 12 + Spring 6) resolved.
- Netty 4.2; template engines upgraded + optional; Jade dropped; multipart replaced/evaluated.
- JUnit 5 with httpclient5 test driver; legacy files gone; VSCode (+launch.json) + .gitignore.
- Version `2.0.0`; deps on `moleculer-java:2.0.0` + `moleculer-java-repl:2.0.0` +
  `datatree-templates:2.0.0`; installed to local `~/.m2`; publishing configured.
