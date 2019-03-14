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
package services.moleculer.web.router;

import services.moleculer.web.middleware.Favicon;
import services.moleculer.web.middleware.NotFound;
import services.moleculer.web.middleware.Redirector;
import services.moleculer.web.middleware.ServeStatic;

/**
 * Prepared route for simple static file service. Usage (with REST services):
 * 
 * <pre>
 * // First route (REST services)
 * RestRoute restServices = new RestRoute();
 * restServices.addAlias("/rest1", "service.action1");
 * restServices.addAlias("GET", "/rest2/:a/:b", "service.action2");
 * restServices.use(new CorsHeaders());
 * apiGateway.addRoute(restServices);
 * 
 * // Last route (HTML pages, CSS and images)
 * StaticRoute webContent = new StaticRoute("/htdocs");
 * webContent.setReloadable(true);
 * apiGateway.addRoute(webContent);
 * </pre>
 */
public class StaticRoute extends Route {

	/**
	 * First middleware redirects "/" to "/index.html"
	 */
	protected Redirector redirector = new Redirector("/", "/index.html", 307);

	/**
	 * Second middleware is the "favicon" handler.
	 */
	protected Favicon favicon = new Favicon();

	/**
	 * Third middleware is static file handler.
	 */
	protected ServeStatic serveStatic = new ServeStatic("/", "/");

	/**
	 * Last middleware produces "Error 404" responses
	 */
	protected NotFound notFound = new NotFound();

	// --- CONSTRUCTORS ---

	public StaticRoute() {
		mappingPolicy = MappingPolicy.ALL;

		// Add middlewares (in REVERSED order)
		use(notFound, serveStatic, favicon, redirector);
	}

	public StaticRoute(String wwwRootDirectory) {
		this();
		serveStatic.setLocalDirectory(wwwRootDirectory);
	}

	// --- DELEGATED METHODS ---

	public String getIndexPage() {
		return redirector.getLocation();
	}

	public void setIndexPage(String location) {
		redirector.setLocation(location);
	}

	public String getFaviconPath() {
		return favicon.getIconPath();
	}

	public void setFaviconPath(String iconPath) {
		favicon.setIconPath(iconPath);
	}

	public String getWebPageDirectory() {
		return serveStatic.getLocalDirectory();
	}

	public void setWebPageDirectory(String wwwRootDirectory) {
		serveStatic.setLocalDirectory(wwwRootDirectory);
	}

	public boolean isEnableReloading() {
		return serveStatic.isEnableReloading();
	}

	public void setEnableReloading(boolean enableReloading) {
		serveStatic.setEnableReloading(enableReloading);
	}

	public String getNotFoundTemplatePath() {
		return notFound.getHtmlTemplatePath();
	}

	public void setNotFoundTemplatePath(String htmlTemplatePath) {
		notFound.setHtmlTemplatePath(htmlTemplatePath);
	}

	// --- GETTERS FOR MIDDLEWARES ---

	public Redirector getRedirector() {
		return redirector;
	}

	public Favicon getFavicon() {
		return favicon;
	}

	public ServeStatic getServeStatic() {
		return serveStatic;
	}

	public NotFound getNotFound() {
		return notFound;
	}

}