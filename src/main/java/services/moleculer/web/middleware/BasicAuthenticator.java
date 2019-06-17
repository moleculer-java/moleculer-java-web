/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2017 Andras Berkes [andras.berkes@programmer.net]<br>
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
package services.moleculer.web.middleware;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.datatree.Tree;
import io.datatree.dom.BASE64;
import io.datatree.dom.Cache;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * Simple middleware that provides HTTP BASIC Authentication support.
 */
@Name("Basic Authenticator")
public class BasicAuthenticator extends HttpMiddleware implements HttpConstants, BasicAuthProvider {

	// --- PROPERTIES ---

	/**
	 * Realm name (visible on login screen).
	 */
	protected String realm = "Moleculer Web";

	/**
	 * Custom auth provider.
	 */
	protected BasicAuthProvider provider = this;

	/**
	 * Max number of cached logins (0 = disable caching)
	 */
	protected int maxCachedLogins = 64;

	// --- USER DATABASE FOR INTERNAL AUTHENTICATION ---

	protected Map<String, String> users = new ConcurrentHashMap<>();

	// --- LOGIN CACHE ---

	protected Cache<String, String> cache = new Cache<>(64);

	// --- CONSTRUCTORS ---

	public BasicAuthenticator() {
	}

	public BasicAuthenticator(BasicAuthProvider authProvider) {
		setProvider(authProvider);
	}

	public BasicAuthenticator(BasicAuthProvider authProvider, int cachedLogins) {
		setProvider(authProvider);
		setMaxCachedLogins(cachedLogins);
	}

	public BasicAuthenticator(String username, String password) {
		addUser(username, password);
	}

	// --- CREATE NEW PROCESSOR ---

	@Override
	public RequestProcessor install(RequestProcessor next, Tree config) {
		return new RequestProcessor() {

			/**
			 * Handles request of the HTTP client.
			 * 
			 * @param req
			 *            WebRequest object that contains the request the client
			 *            made of the ApiGateway
			 * @param rsp
			 *            WebResponse object that contains the response the
			 *            ApiGateway returns to the client
			 * 
			 * @throws Exception
			 *             if an input or output error occurs while the
			 *             ApiGateway is handling the HTTP request
			 */
			@Override
			public void service(WebRequest req, WebResponse rsp) throws Exception {

				// Get "Authorization" header
				String authorization = req.getHeader(AUTHORIZATION);
				if (authorization == null) {
					sendUnauthorized(rsp);
					return;
				}

				// Process header
				try {

					// Get from cache
					String username = null;
					if (maxCachedLogins > 0) {
						username = cache.get(authorization);
					}

					// Parse header
					if (username == null) {
						String[] tokens = authorization.split(" ");
						if (tokens.length != 2) {
							sendUnauthorized(rsp);
							return;
						}
						String login = new String(BASE64.decode(tokens[1]));
						int i = login.indexOf(":");
						String password;
						if (i > -1) {
							username = login.substring(0, i);
							password = login.substring(i + 1);
						} else {
							username = login;
							password = "";
						}
						if (provider.authenticate(broker, username, password)) {
							if (maxCachedLogins > 0) {
								cache.put(authorization, username);
							}							
						} else {
							username = null;
						}
					}

					// Check response
					if (username == null) {
						sendUnauthorized(rsp);
						return;
					}
					
					// Store username in properties
					rsp.setProperty(PROPERTY_USER, username);

				} catch (Throwable cause) {
					logger.error("Unable to parse \"Authorization\" header!", cause);
					sendUnauthorized(rsp);
					return;
				}

				// Invoke next handler (eg. Moleculer Action)
				next.service(req, rsp);
			}

		};
	}

	protected void sendUnauthorized(WebResponse rsp) throws Exception {
		try {
			rsp.setStatus(401);
			rsp.setHeader(CONTENT_LENGTH, "0");
			rsp.setHeader(WWW_AUTHENTICATE, "Basic realm=\"" + realm + "\"");
		} finally {
			rsp.end();
		}
	}

	// --- SIMPLE AUTHENTICATION PROVIDER ---

	@Override
	public boolean authenticate(ServiceBroker broker, String username, String password) {
		if (users == null) {
			return false;
		}
		String savedPassword = users.get(username);
		if (savedPassword == null) {

			// Unknown user -> FAIL
			return false;
		}
		return savedPassword.equals(password);
	}

	public BasicAuthenticator addUser(String username, String password) {
		if (users == null) {
			users = new ConcurrentHashMap<>();
		}
		users.put(username, password);
		cache.clear();
		return this;
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public String getRealm() {
		return realm;
	}

	public void setRealm(String realm) {
		this.realm = Objects.requireNonNull(realm);
	}

	public BasicAuthProvider getProvider() {
		return provider;
	}

	public void setProvider(BasicAuthProvider authProvider) {
		this.provider = Objects.requireNonNull(authProvider);
	}

	public int getMaxCachedLogins() {
		return maxCachedLogins;
	}

	public void setMaxCachedLogins(int cachedLogins) {
		if (this.maxCachedLogins == cachedLogins) {
			return;
		}
		this.maxCachedLogins = cachedLogins;
		if (cachedLogins > 0) {
			cache = new Cache<>(cachedLogins);
		} else {
			cache = null;
		}
	}

	public Map<String, String> getUsers() {
		return users;
	}

	public void setUsers(Map<String, String> users) {
		this.users = users;
		if (cache != null) {
			cache.clear();
		}
	}

}