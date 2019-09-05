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
package services.moleculer.web;

import services.moleculer.ServiceBroker;
import services.moleculer.service.Action;
import services.moleculer.service.Service;
import services.moleculer.web.netty.NettyServer;

/**
 * This is the easiest way to create a REST service with Moleculer.<br>
 * <br>
 * Invoke with GET method (eg. from browser):<br>
 * URL: http://localhost:8080/math/add?a=3&b=6<br>
 * <br>
 * Invoke with POST method (eg. from JavaScript):<br>
 * URL: http://localhost:8080/math/add<br>
 * Body: {"a":3,"b":5}
 */
@SuppressWarnings("unused")
public class Sample {

	public static void main(String[] args) throws Exception {
	
		new ServiceBroker()
		   .createService(new NettyServer(8080))
		   .createService(new ApiGateway("*"))
		   .createService(new Service("math") {
			public Action add = ctx -> {
				return ctx.params.get("a", 0) +
		        	   ctx.params.get("b", 0);
		        };
		    }).start();

	}

}