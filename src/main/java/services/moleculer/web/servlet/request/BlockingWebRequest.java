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
package services.moleculer.web.servlet.request;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.synchronoss.cloud.nio.multipart.BlockingIOAdapter.ParserToken;
import org.synchronoss.cloud.nio.multipart.BlockingIOAdapter.Part;
import org.synchronoss.cloud.nio.multipart.Multipart;
import org.synchronoss.cloud.nio.multipart.MultipartContext;
import org.synchronoss.cloud.nio.multipart.MultipartUtils;
import org.synchronoss.cloud.nio.multipart.util.collect.CloseableIterator;

import services.moleculer.ServiceBroker;
import services.moleculer.stream.PacketStream;
import services.moleculer.web.WebRequest;

public class BlockingWebRequest implements WebRequest {

	// --- REQUEST VARIABLES ----

	protected final HttpServletRequest req;

	protected final String method;

	protected final int contentLength;

	protected final String contentType;

	protected final boolean multipart;
	
	// --- BODY PROCESSORS ---

	protected PacketStream stream;

	// --- CONSTRUCTOR ---

	public BlockingWebRequest(ServiceBroker broker, HttpServletRequest req) throws IOException {

		// Store request
		this.req = req;

		// Get method
		method = req.getMethod();

		// Get content type
		contentType = req.getContentType();

		// Has body?
		if (!"POST".equals(method) && !"PUT".equals(method)) {

			// Not POST or PUT -> not a stream
			multipart = false;
			contentLength = 0;
			return;
		}
		contentLength = req.getContentLength();
		if (contentLength == 0) {

			// Zero Content Length -> not a stream
			multipart = false;
			return;
		}

		// Multipart content?
		multipart = MultipartUtils.isMultipart(contentType);

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
			while (in.isReady() && (len = in.read(buffer)) != -1) {
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
		CloseableIterator<ParserToken> i = Multipart.multipart(context).forBlockingIO(in);
		boolean close = true;
		try {
			while (i.hasNext()) {
				ParserToken token = i.next();
				if (token.getType() == ParserToken.Type.PART) {
					Part part = (Part) token;
					if (!MultipartUtils.isFormField(part.getHeaders(), context)) {
						InputStream body = part.getPartBody();
						close = false;
						stream.transferFrom(body).then(ok -> {
							i.close();
							stream.sendClose();
						}).catchError(err -> {
							i.close();
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
				if (i != null) {
					try {
						i.close();
					} catch (Throwable ignored) {
					}
				}
			}
		}
	}

	// --- PROPERTY GETTERS ---

	@Override
	public String getAddress() {
		return req.getRemoteAddr();
	}

	@Override
	public String getMethod() {
		return method;
	}

	@Override
	public String getPath() {
		return req.getPathInfo();
	}

	@Override
	public String getQuery() {
		return req.getQueryString();
	}

	@Override
	public int getContentLength() {
		return contentLength;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public PacketStream getBody() {
		return stream;
	}

	@Override
	public String getHeader(String name) {
		return req.getHeader(name);
	}

	@Override
	public Iterator<String> getHeaders() {
		final Enumeration<String> e = req.getHeaderNames();
		return new Iterator<String>() {

			@Override
			public final boolean hasNext() {
				return e.hasMoreElements();
			}

			@Override
			public String next() {
				return e.nextElement();
			}

		};
	}

	@Override
	public boolean isMultipart() {
		return multipart;
	}
	
}
