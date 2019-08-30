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

import services.moleculer.web.middleware.ErrorPage;

/**
 * Prepared route for REST services. Usage (with StaticRoute):
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
public class RestRoute extends Route {

	// --- CUSTOM ERROR PAGE ---
	
	protected ErrorPage errorPage = new ErrorPage();
	
	// --- COSTRUCTORS ---
	
	public RestRoute() {
		use(errorPage);
	}

	public RestRoute(String errorTemplatePath) {
		use(errorPage);
		errorPage.setHtmlTemplatePath(errorTemplatePath);
	}
	
	// --- DELEGATED METHODS ---
	
	public String getErrorTemplatePath() {
		return errorPage.getHtmlTemplatePath();
	}

	public void setErrorTemplatePath(String htmlTemplatePath) {
		errorPage.setHtmlTemplatePath(htmlTemplatePath);
	}

	// --- GETTERS FOR MIDDLEWARES ---
	
	public ErrorPage getErrorPage() {
		return errorPage;
	}
	
}