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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import io.datatree.Tree;
import services.moleculer.cacher.Cacher;
import services.moleculer.cacher.MemoryCacher;
import services.moleculer.util.CheckedTree;
import services.moleculer.web.WebRequest;
import services.moleculer.web.common.HttpConstants;

public class DefaultSessionStore implements SessionStore, HttpConstants {

	// --- CACHE WAIT TIMEOUT ---

	protected static final long cacheTimeout = Long.parseLong(System.getProperty("session.store.timeout", "5000"));

	// --- CACHER FOR NETTY SESSIONS ---

	protected final Cacher cacher;

	// --- SESSION TIMEOUT FOR NETTY AND J2EE SESSIONS ---

	protected final int sessionTimeout;

	// --- CONSTRUCTOR ---

	public DefaultSessionStore(Cacher cacher, int sessionTimeout) {
		this.cacher = cacher == null ? new MemoryCacher() : cacher;
		this.sessionTimeout = sessionTimeout;
	}

	// --- LOAD SESSION DATA ---

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Object> get(String sessionID, WebRequest req) throws Exception {
		Object internalRequest = req.getInternalObject();
		if (internalRequest instanceof HttpServletRequest) {

			// J2EE runtime
			HttpServletRequest servletRequest = (HttpServletRequest) internalRequest;
			HttpSession httpSession = servletRequest.getSession(true);
			return (Map<String, Object>) httpSession.getAttribute(META_SESSION);
		}

		// Netty runtime
		Tree container = cacher.get("sessions." + sessionID).waitFor(cacheTimeout);
		if (container != null) {
			return (Map<String, Object>) container.asObject();
		}
		return null;
	}

	// --- SAVE SESSION DATA ---

	@Override
	public void set(String sessionID, WebRequest req, Map<String, Object> sessionData) throws Exception {
		Object internalRequest = req.getInternalObject();
		if (internalRequest instanceof HttpServletRequest) {

			// J2EE runtime
			HttpServletRequest servletRequest = (HttpServletRequest) internalRequest;
			HttpSession httpSession = servletRequest.getSession(true);
			httpSession.setMaxInactiveInterval(sessionTimeout);
			httpSession.setAttribute(META_SESSION, sessionData);

		} else {

			// Netty runtime
			cacher.set("sessions." + sessionID, new CheckedTree(sessionData), sessionTimeout);
		}
	}

}