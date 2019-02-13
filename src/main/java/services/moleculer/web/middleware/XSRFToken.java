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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;

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
 * This middleware adds  "X-XSRF-TOKEN" header to responses.
 */
@Name("XSRF Token")
public class XSRFToken extends HttpMiddleware implements HttpConstants {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(XSRFToken.class);

	// --- PROPERTIES ---

	protected String headerName = "X-XSRF-TOKEN";
	protected String secret = Long.toHexString(Math.abs(System.nanoTime()));
	protected long timeout = 3600000;

	// --- THREAD-SAFE GENERATORS / VALIDATORS ---

	protected ThreadLocal<TokenHandler> tokenHandlers = new ThreadLocal<>();

	// --- CONSTRUCTORS ---

	public XSRFToken() {
	}

	public XSRFToken(long timeoutMillis) {
		setTimeout(timeoutMillis);
	}

	public XSRFToken(long timeoutMillis, String headerName, String secret) {
		setTimeout(timeoutMillis);
		setHeaderName(headerName);
		setSecret(secret);
	}

	// --- TOKEN GENERATOR / VALIDATOR ---

	protected static class TokenHandler {

		// --- THREAD VARIABLES ---

		protected final long timeout;
		protected final Mac mac;
		protected final SecureRandom rnd;
		protected final byte[] salt;

		// --- CONSTRUCTOR ---

		protected TokenHandler(String secret, long timeout) throws NoSuchAlgorithmException, InvalidKeyException {
			this.timeout = timeout;
			mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
			rnd = new SecureRandom();
			salt = new byte[32];
		}

		// --- TOKEN GENERATOR ---

		protected String nextToken() throws NoSuchAlgorithmException {
			rnd.nextBytes(salt);
			String prefix = BASE64.encode(salt) + "." + Long.toString(System.currentTimeMillis());
			String hash = BASE64.encode(mac.doFinal(prefix.getBytes()));
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
				String hash = BASE64.encode(mac.doFinal(prefix.getBytes()));

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
			handler = new TokenHandler(secret, timeout);
			tokenHandlers.set(handler);
		}
		return handler;
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

				// Switch by method
				switch (req.getMethod()) {
				case GET:

					// Set token and continue processing...
					String token = getThreadHandler().nextToken();
					rsp.setHeader(headerName, token);
					break;

				case POST:
				case PUT:
				case DELETE:
				case PATCH:

					// Validate mandatory token
					token = req.getHeader(headerName);
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
				default:
					break;
				}

				// Invoke next handler (eg. Moleculer Action)
				next.service(req, rsp);
			}
		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public String getHeaderName() {
		return headerName;
	}

	public void setHeaderName(String headerName) {
		this.headerName = Objects.requireNonNull(headerName);
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = Objects.requireNonNull(secret);
	}

	public long getTimeout() {
		return timeout;
	}

	public void setTimeout(long timeoutMillis) {
		this.timeout = timeoutMillis;
	}

}