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

import java.nio.charset.StandardCharsets;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.context.Context;
import services.moleculer.service.Action;
import services.moleculer.service.Middleware;
import services.moleculer.service.Name;
import services.moleculer.web.common.HttpConstants;

@Name("Not Found")
public class NotFound extends Middleware implements HttpConstants {

	// --- JSON / HTML RESPONSE ---

	protected boolean htmlResponse;

	// --- CREATE NEW ACTION ---

	public Action install(Action action, Tree config) {
		return new Action() {

			@Override
			public Object handler(Context ctx) throws Exception {

				// Get path
				String path = ctx.params.getMeta().get(PATH, "/");

				// 404 Not Found
				Tree rsp = new Tree();
				Tree meta = rsp.getMeta();
				meta.put(STATUS, 404);
				Tree headers = meta.putMap(HEADERS, true);
				if (htmlResponse) {
					headers.put(RSP_CONTENT_TYPE, "text/html;charset=utf-8");
					StringBuilder body = new StringBuilder(512);
					body.append("<html><body><h1>Not found: ");
					body.append(path);
					body.append("</h1><hr/>");
					body.append("Moleculer V");
					body.append(ServiceBroker.SOFTWARE_VERSION);
					body.append("</body></html>");
					rsp.setObject(body.toString().getBytes(StandardCharsets.UTF_8));
				} else {
					headers.put(RSP_CONTENT_TYPE, "application/json;charset=utf-8");
					rsp.put("message", "Not Found: " + path);
				}
				return rsp;
			}

		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public boolean isHtmlResponse() {
		return htmlResponse;
	}

	public void setHtmlResponse(boolean htmlResponse) {
		this.htmlResponse = htmlResponse;
	}

}