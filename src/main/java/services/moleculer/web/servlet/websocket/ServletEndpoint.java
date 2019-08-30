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
package services.moleculer.web.servlet.websocket;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;

import services.moleculer.web.common.Endpoint;

public class ServletEndpoint implements Endpoint {

	protected final Session session;
	protected final RemoteEndpoint.Basic basic;

	protected ServletEndpoint(Session session, boolean openRemote) {
		this.session = session;
		this.basic = openRemote ? session.getBasicRemote() : null;
	}

	@Override
	public void send(String message) {
		try {
			basic.sendText(message);			
		} catch (Exception ignored) {
		}
	}

	@Override
	public boolean isOpen() {
		return session.isOpen();
	}

	@Override
	public Object getInternal() {
		return session;
	}

	@Override
	public int hashCode() {
		return session.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof Endpoint)) {
			return false;
		}
		Endpoint e = (Endpoint) obj;
		return e.getInternal() == session;
	}

}