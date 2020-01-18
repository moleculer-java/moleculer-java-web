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

import static services.moleculer.web.common.GatewayUtils.getCookieValue;
import static services.moleculer.web.common.GatewayUtils.setCookie;

import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import io.datatree.dom.BASE64;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * This middleware adds "X-XSRF-TOKEN" header to responses. Sample:
 * <pre>
 * restRoute.use(new XSRFToken());
 * </pre>
 */
@Name("XSRF Token")
public class XSRFToken extends HttpMiddleware implements HttpConstants {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(XSRFToken.class);

	// --- PROPERTIES ---

	/**
	 * Name of the HTTP-header.
	 */
	protected String headerName = "X-XSRF-TOKEN";

	/**
	 * Name of the HTTP-cookie.
	 */
	protected String cookieName = "XSRF-TOKEN";

	/**
	 * Secret.
	 */
	protected String secret = Long.toHexString(Math.abs(System.nanoTime()));

	/**
	 * Cookie path.
	 */
	protected String path = "/";

	/**
	 * Cookie / token timeout in SECONDS.
	 */
	protected long maxAge = 1800;

	/**
	 * Enable XSRF-TOKEN cookie.
	 */
	protected boolean enableCookie = true;

	// --- THREAD-SAFE GENERATORS / VALIDATORS ---

	protected ThreadLocal<TokenHandler> tokenHandlers = new ThreadLocal<>();

	// --- CONSTRUCTORS ---

	public XSRFToken() {
	}

	public XSRFToken(long maxAge) {
		setMaxAge(maxAge);
	}

	// --- TOKEN GENERATOR / VALIDATOR ---

	protected static class TokenHandler {

		// --- THREAD VARIABLES ---

		protected final long timeout;
		protected final Mac sha256;
		protected final SecureRandom rnd;
		protected final byte[] salt;

		// --- CONSTRUCTOR ---

		protected TokenHandler(String secret, long maxAge) throws NoSuchAlgorithmException, InvalidKeyException {
			timeout = maxAge * 1000L;
			sha256 = Mac.getInstance("HmacSHA256");
			sha256.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
			rnd = new SecureRandom();
			salt = new byte[32];
		}

		// --- TOKEN GENERATOR ---

		protected String nextToken() throws NoSuchAlgorithmException {
			rnd.nextBytes(salt);
			String prefix = BASE64.encode(salt) + "." + Long.toString(System.currentTimeMillis());
			String hash = BASE64.encode(sha256.doFinal(prefix.getBytes(StandardCharsets.US_ASCII)));
			return prefix + "." + hash;
		}

		// --- TOKEN VALIDATOR ---

		protected boolean isValid(String token) {
			try {

				// Empty or missing?
				if (token == null || token.isEmpty()) {
					return false;
				}

				// Split token
				String[] parts = token.split("\\.");
				if (parts.length != 3) {
					return false;
				}

				// Create prefix and hash
				String prefix = parts[0] + "." + parts[1];
				String hash = BASE64.encode(sha256.doFinal(prefix.getBytes(StandardCharsets.US_ASCII)));

				// Check hash
				if (!hash.equals(parts[2])) {
					return false;
				}

				// Check time
				return System.currentTimeMillis() <= Long.parseLong(parts[1]) + timeout;

			} catch (Throwable cause) {
				logger.warn("Unable to decode token!", cause);
			}
			return false;
		}

	}

	protected TokenHandler getThreadHandler() throws InvalidKeyException, NoSuchAlgorithmException {
		TokenHandler handler = tokenHandlers.get();
		if (handler == null) {
			handler = new TokenHandler(secret, maxAge);
			tokenHandlers.set(handler);
		}
		return handler;
	}

	// --- CREATE NEW PROCESSOR ---

	@Override
	public RequestProcessor install(RequestProcessor next, Tree config) {
		return new AbstractRequestProcessor(next) {

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

				// Switch by method
				switch (req.getMethod()) {
				case POST:
				case PUT:
				case DELETE:
				case PATCH:

					// Validate mandatory token
					String token = req.getHeader(headerName);
					if ((token == null || token.isEmpty()) && enableCookie) {
						token = getCookieValue(req, rsp, cookieName);
					}
					if (!getThreadHandler().isValid(token)) {

						// Invalid token!
						try {
							rsp.setStatus(403);
							rsp.setHeader(CONTENT_LENGTH, "0");
						} finally {
							rsp.end();
						}
						return;
					}
					break;

				case GET:

					// Generate new token and continue processing...
					token = getThreadHandler().nextToken();
					rsp.setHeader(headerName, token);
					if (enableCookie) {
						HttpCookie cookie = new HttpCookie(cookieName, token);
						cookie.setPath(path);
						cookie.setMaxAge(maxAge);
						setCookie(rsp, cookie);
					}
					break;

				default:
					break;
				}

				// Invoke next handler (eg. Moleculer Action)
				next.service(req, rsp);
			}
		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	/**
	 * @return the cookieName
	 */
	public String getCookieName() {
		return cookieName;
	}

	/**
	 * @param cookieName
	 *            the cookieName to set
	 */
	public void setCookieName(String cookieName) {
		this.cookieName = cookieName;
	}

	/**
	 * @return the path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @param path
	 *            the path to set
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * @return the maxAge
	 */
	public long getMaxAge() {
		return maxAge;
	}

	/**
	 * @param maxAge
	 *            the maxAge to set
	 */
	public void setMaxAge(long maxAge) {
		this.maxAge = maxAge;
	}

	/**
	 * @return the headerName
	 */
	public String getHeaderName() {
		return headerName;
	}

	/**
	 * @param headerName
	 *            the headerName to set
	 */
	public void setHeaderName(String headerName) {
		this.headerName = headerName;
	}

	/**
	 * @return the secret
	 */
	public String getSecret() {
		return secret;
	}

	/**
	 * @param secret
	 *            the secret to set
	 */
	public void setSecret(String secret) {
		this.secret = secret;
	}

	/**
	 * @return the enableCookie
	 */
	public boolean isEnableCookie() {
		return enableCookie;
	}

	/**
	 * @param enableCookie
	 *            the enableCookie to set
	 */
	public void setEnableCookie(boolean enableCookie) {
		this.enableCookie = enableCookie;
	}

}