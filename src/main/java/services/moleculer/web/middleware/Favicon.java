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

import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import io.datatree.Tree;
import services.moleculer.context.Context;
import services.moleculer.service.Action;
import services.moleculer.service.Middleware;
import services.moleculer.service.Name;
import services.moleculer.util.CheckedTree;
import services.moleculer.web.common.HttpConstants;

@Name("Favicon")
public class Favicon extends Middleware implements HttpConstants {

	// --- PROPERTIES ---

	protected String iconPath;
	protected int maxAge;

	// --- CACHED RESPONSE ---

	protected Tree response;

	// --- CONSTRUCTORS ---

	public Favicon() {
		this("/favicon.ico");
	}

	public Favicon(String pathToIcon) {
		setIconPath(pathToIcon);
	}

	// --- CREATE NEW ACTION ---

	public Action install(Action action, Tree config) {
		return new Action() {

			@Override
			public Object handler(Context ctx) throws Exception {
				if ("/favicon.ico".equals(ctx.params.getMeta().get(PATH, "/"))) {
					return response;
				}
				return action.handler(ctx);
			}

		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public String getIconPath() {
		return iconPath;
	}

	public void setIconPath(String iconPath) {
		this.iconPath = iconPath;
		byte[] bytes = readAllBytes(iconPath);
		if (bytes.length == 0) {
			throw new IllegalArgumentException("File or resource not found: " + iconPath);
		}
		response = new CheckedTree(bytes);
		Tree headers = response.getMeta().putMap(HEADERS);
		if (maxAge > 0) {
			headers.put(RSP_CACHE_CONTROL, "public, max-age=" + maxAge);
		}
		headers.put(RSP_CONTENT_TYPE, "image/x-icon");
	}

	public int getMaxAge() {
		return maxAge;
	}

	public void setMaxAge(int maxAge) {
		this.maxAge = maxAge;
	}

}