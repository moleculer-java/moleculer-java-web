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

import static services.moleculer.web.common.GatewayUtils.getCookie;
import static services.moleculer.web.common.GatewayUtils.setCookie;

import java.net.HttpCookie;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.datatree.Tree;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * Generates Session Cookies, and sets the cookie header.
 */
@Name("Session Cookie Handler")
public class SessionCookie extends HttpMiddleware implements HttpConstants {

	// --- PROPERTIES ---

	/**
	 * Cookie name.
	 */
	protected String cookieName = "JSESSIONID";

	/**
	 * Cookie path.
	 */
	protected String path = "/";

	/**
	 * Cookie timeout in SECONDS (0 = no timeout).
	 */
	protected long maxAge = 0;

	// --- VARIABLES ---

	protected AtomicLong rnd = new AtomicLong(System.nanoTime());

	protected AtomicLong seq = new AtomicLong();

	// --- CACHE ---

	protected WeakHashMap<String, HttpCookie> cache = new WeakHashMap<>(512);

	// --- CONSTRUCTORS ---

	public SessionCookie() {
	}

	public SessionCookie(String cookieName) {
		setCookieName(cookieName);
	}

	public SessionCookie(String cookieName, String path) {
		setCookieName(cookieName);
		setPath(path);
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

				// Get sessionID
				HttpCookie cookie = getCookie(req, rsp, cookieName);

				// Has cookie?
				String sessionID;
				if (cookie == null) {

					// Generate new sessionID
					sessionID = nextID();

				} else {

					// Get the previous sessionID
					sessionID = cookie.getValue();
				}

				// Write cookie into response
				cookie = cache.get(sessionID);
				if (cookie == null) {
					cookie = new HttpCookie(cookieName, sessionID);
					cookie.setPath(path);
					if (maxAge > 0) {
						cookie.setMaxAge(maxAge);
					}
					cache.put(sessionID, cookie);
				}
				setCookie(req, rsp, cookie);

				// Store sessionID in request
				rsp.setProperty(PROPERTY_SESSION_ID, sessionID);

				// Invoke next handler
				next.service(req, rsp);
			}

		};
	}

	// --- SESSION-ID GENERATOR ---

	protected String nextID() {

		// Generate pseudo random long (XORShift is the fastest random method)
		long start;
		long next;
		do {
			start = rnd.get();
			next = start + 1;
			next ^= (next << 21);
			next ^= (next >>> 35);
			next ^= (next << 4);
		} while (!rnd.compareAndSet(start, next));

		// Add sequence prefix
		return Long.toString(seq.incrementAndGet(), 36) + '|' + Long.toString(next, 36);
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public String getCookieName() {
		return cookieName;
	}

	public void setCookieName(String cookieName) {
		if (!this.cookieName.equals(cookieName)) {
			cache.clear();
		}
		this.cookieName = Objects.requireNonNull(cookieName);
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		if (!this.path.equals(path)) {
			cache.clear();
		}
		this.path = path;
	}

}