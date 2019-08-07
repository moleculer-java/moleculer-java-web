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
package services.moleculer.web.servlet.request;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.synchronoss.cloud.nio.multipart.BlockingIOAdapter.ParserToken;
import org.synchronoss.cloud.nio.multipart.BlockingIOAdapter.Part;
import org.synchronoss.cloud.nio.multipart.Multipart;
import org.synchronoss.cloud.nio.multipart.MultipartContext;
import org.synchronoss.cloud.nio.multipart.MultipartUtils;
import org.synchronoss.cloud.nio.multipart.util.collect.CloseableIterator;

import services.moleculer.ServiceBroker;

public class BlockingWebRequest extends AbstractWebRequest {

	// --- CONSTRUCTOR ---

	public BlockingWebRequest(ServiceBroker broker, HttpServletRequest req) throws IOException {
		super(req);

		// Create body stream
		if (multipart) {
			createMultipartStream(broker);
		} else {
			createStream(broker);
		}
	}

	// --- NORMAL POST ---

	protected void createStream(ServiceBroker broker) throws IOException {
		ServletInputStream in = req.getInputStream();
		stream = broker.createStream();
		try {
			int len = in.available();
			if (len < 1024 || len > 1048576) {
				len = 2048;
			}
			final byte buffer[] = new byte[len];
			while ((len = in.read(buffer)) != -1) {
				if (len > 0) {
					byte[] copy = new byte[len];
					System.arraycopy(buffer, 0, copy, 0, len);
					stream.sendData(copy);
				}
			}
		} catch (Throwable cause) {
			stream.sendError(cause);
		} finally {
			stream.sendClose();
		}
	}

	// --- MULTIPART POST ---

	protected void createMultipartStream(ServiceBroker broker) throws IOException {
		stream = broker.createStream();
		MultipartContext context = new MultipartContext(contentType, contentLength, null);
		InputStream in = req.getInputStream();
		CloseableIterator<ParserToken> tokenIterator = Multipart.multipart(context).forBlockingIO(in);
		boolean close = true;
		try {
			while (tokenIterator.hasNext()) {
				ParserToken token = tokenIterator.next();
				if (token.getType() == ParserToken.Type.PART) {
					Part part = (Part) token;
					if (!MultipartUtils.isFormField(part.getHeaders(), context)) {
						InputStream body = part.getPartBody();
						close = false;
						stream.transferFrom(body).then(ok -> {
							tokenIterator.close();
							stream.sendClose();
						}).catchError(err -> {
							tokenIterator.close();
							stream.sendError(err);
						});
						return;
					}
				}
			}
		} catch (Throwable cause) {
			stream.sendError(cause);
		} finally {
			if (close) {
				stream.sendClose();
				if (tokenIterator != null) {
					try {
						tokenIterator.close();
					} catch (Throwable ignored) {
					}
				}
			}
		}
	}
	
}