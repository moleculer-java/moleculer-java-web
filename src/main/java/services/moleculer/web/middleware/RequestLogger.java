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

import static services.moleculer.util.CommonUtils.formatNamoSec;
import static services.moleculer.util.CommonUtils.formatNumber;

import java.io.PrintWriter;
import java.io.StringWriter;

import io.datatree.Promise;
import io.datatree.Tree;
import services.moleculer.context.Context;
import services.moleculer.service.Action;
import services.moleculer.service.Middleware;
import services.moleculer.service.Name;
import services.moleculer.web.common.HttpConstants;

@Name("Request Logger")
public class RequestLogger extends Middleware implements HttpConstants {

	// --- NEW LINE ---

	protected static final char[] CR_LF = System.getProperty("line.separator", "\r\n").toCharArray();

	// --- CREATE NEW ACTION ---

	public Action install(Action action, Tree config) {
		return new Action() {

			@Override
			public Object handler(Context ctx) throws Exception {
				long start = System.nanoTime();
				Object result = action.handler(ctx);
				return Promise.resolve(result).then(rsp -> {
					long duration = System.nanoTime() - start;
					StringBuilder tmp = new StringBuilder(512);
					tmp.append("======= REQUEST PROCESSED IN ");
					tmp.append(formatNamoSec(duration).toUpperCase());
					tmp.append(" =======");
					tmp.append(CR_LF);
					tmp.append("Request:");
					tmp.append(CR_LF);
					tmp.append(ctx.params);
					tmp.append(CR_LF);
					tmp.append("Response:");
					tmp.append(CR_LF);
					if (rsp == null) {
						tmp.append("<null>");
					} else {
						if (rsp.isMap() || rsp.isList()) {
							tmp.append(rsp);
						} else {
							tmp.append(rsp.getMeta());
							if (rsp.getType() == byte[].class) {
								byte[] bytes = (byte[]) rsp.asBytes();
								if (bytes.length == 0) {
									tmp.append(CR_LF);
									tmp.append("<empty body>");
								} else {
									tmp.append(CR_LF);
									tmp.append('<');
									tmp.append(formatNumber(bytes.length));
									tmp.append(" bytes of binary response>");
								}
							} else {
								tmp.append(CR_LF);
								tmp.append(rsp);
							}
						}
					}
					logger.info(tmp.toString());
				}).catchError(cause -> {
					long duration = System.nanoTime() - start;
					StringBuilder tmp = new StringBuilder(512);
					tmp.append("======= REQUEST PROCESSED IN ");
					tmp.append(formatNamoSec(duration).toUpperCase());
					tmp.append(" =======");
					tmp.append(CR_LF);
					tmp.append("Request:");
					tmp.append(CR_LF);
					tmp.append(ctx.params);
					tmp.append(CR_LF);
					tmp.append("Response:");
					tmp.append(CR_LF);
					StringWriter stringWriter = new StringWriter(512);
					PrintWriter printWriter = new PrintWriter(stringWriter, true);
					cause.printStackTrace(printWriter);
					tmp.append(stringWriter.toString().trim());
					logger.error(tmp.toString());
					return cause;
				});
			}

		};
	}

}