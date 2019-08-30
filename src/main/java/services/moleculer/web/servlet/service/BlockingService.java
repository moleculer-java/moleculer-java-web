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
package services.moleculer.web.servlet.service;

import java.util.concurrent.TimeoutException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import services.moleculer.ServiceBroker;
import services.moleculer.web.ApiGateway;
import services.moleculer.web.servlet.request.BlockingWebRequest;
import services.moleculer.web.servlet.response.BlockingWebResponse;

/**
 * Blocking request processing mode.
 */
public class BlockingService implements ServiceMode {

	protected final long timeout;
	
	public BlockingService(long timeout) {
		this.timeout = timeout;
	}
	
	@Override
	public void service(ServiceBroker broker, ApiGateway gateway, HttpServletRequest request, HttpServletResponse response) throws Exception {
		try {

			// Blocking service
			BlockingWebResponse bwr = new BlockingWebResponse(response);
			gateway.service(new BlockingWebRequest(broker, request), bwr);
			bwr.waitFor(timeout);

		} catch (TimeoutException timeout) {
			try {
				response.sendError(408);
			} catch (Throwable ignored) {
			}
		}
	}

}