/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2020 Andras Berkes [andras.berkes@programmer.net]<br>
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
package services.moleculer.web.middleware.session;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.cacher.Cacher;
import services.moleculer.web.CallProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;
import services.moleculer.web.router.Route;

/**
 * <pre>
 * // Store "$session" block (in meta)
 * SessionHandler sessionHandler = new SessionHandler(broker);
 * gateway.setBeforeCall(sessionHandler.beforeCall());
 * gateway.setAfterCall(sessionHandler.afterCall());
 * 
 * // Session cookie handling
 * gateway.use(new SessionCookie());
 * </pre>
 */
public class SessionHandler implements HttpConstants {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(SessionHandler.class);

	// --- SESSION STORE ---

	protected final SessionStore store;

	// --- CONSTRUCTORS ---

	public SessionHandler(ServiceBroker broker) {
		this(broker, 60 * 30);
	}
	
	public SessionHandler(ServiceBroker broker, int sessionTimeout) {
		this(broker.getConfig().getCacher(), sessionTimeout);
	}

	public SessionHandler(Cacher cacher, int sessionTimeout) {
		this(new DefaultSessionStore(cacher, sessionTimeout));
	}

	public SessionHandler(SessionStore store) {
		this.store = store;
	}

	// --- BEFORE CALL PROCESSOR ---

	public CallProcessor beforeCall() {
		return new CallProcessor() {

			@Override
			public final void onCall(Route route, WebRequest req, WebResponse rsp, Tree data) {
				try {

					// Get sessionID - SessionCookie Middleware is required
					String sessionID = (String) rsp.getProperty(PROPERTY_SESSION_ID);
					if (sessionID == null || data == null) {
						return;
					}

					// Load "$session" block from SessionStore
					Map<String, Object> map = store.get(sessionID, req);
					Tree meta = data.getMeta();
					Tree session = meta.putMap(META_SESSION);
					if (map != null) {
						session.setObject(map);
					}

				} catch (Exception cause) {
					logger.error("Unable to load session data!", cause);
				}
			}
		};
	}

	// --- AFTER CALL PROCESSOR ---

	public CallProcessor afterCall() {
		return new CallProcessor() {

			@SuppressWarnings("unchecked")
			@Override
			public final void onCall(Route route, WebRequest req, WebResponse rsp, Tree data) {
				try {

					// Get sessionID
					String sessionID = (String) rsp.getProperty(PROPERTY_SESSION_ID);
					if (sessionID == null || data == null) {
						return;
					}

					// Get meta
					Tree meta = data.getMeta(false);
					if (meta == null) {
						return;
					}

					// Get "$session" block
					Tree session = meta.get(META_SESSION);
					if (session == null || session.isEmpty()) {
						return;
					}

					// Save "$session" block into SessionStore
					store.set(sessionID, req, (Map<String, Object>) session.asObject());
					
				} catch (Exception cause) {
					logger.error("Unable to save session data!", cause);
				}
			}
		};
	}

}