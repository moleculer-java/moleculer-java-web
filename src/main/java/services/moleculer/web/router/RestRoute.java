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