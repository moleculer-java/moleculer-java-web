/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2018 Andras Berkes [andras.berkes@programmer.net]<br>
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
package services.moleculer.web.servlet.response;

import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import services.moleculer.web.WebResponse;

public class NonBlockingWebResponse implements WebResponse {

	protected final AsyncContext async;
	protected final HttpServletResponse rsp;
	protected final ServletOutputStream out;

	public NonBlockingWebResponse(AsyncContext async, HttpServletResponse rsp) throws IOException {
		this.async = async;
		this.rsp = rsp;
		this.out = (ServletOutputStream) rsp.getOutputStream();
	}

	@Override
	public void setStatus(int code) {
		rsp.setStatus(code);
	}

	@Override
	public void setHeader(String name, String value) {
		rsp.setHeader(name, value);
	}

	@Override
	public void send(byte[] bytes) throws IOException {
		out.write(bytes);
	}

	@Override
	public void end() {
		try {
			out.close();
		} catch (Exception ignored) {
		}
		async.complete();
	}

}
