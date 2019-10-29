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

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.synchronoss.cloud.nio.multipart.Multipart;
import org.synchronoss.cloud.nio.multipart.MultipartContext;
import org.synchronoss.cloud.nio.multipart.NioMultipartParser;

import services.moleculer.ServiceBroker;
import services.moleculer.web.common.ParserListener;

public class NonBlockingWebRequest extends AbstractWebRequest {

	// --- CONSTRUCTOR ---

	public NonBlockingWebRequest(ServiceBroker broker, AsyncContext async) throws IOException {
		super((HttpServletRequest) async.getRequest());

		// Create body stream
		if (contentLength != 0) {
			if (multipart) {
				createMultipartStream(broker, async);
			} else {
				createStream(broker, async);
			}
		}
	}

	// --- NORMAL POST ---

	protected void createStream(ServiceBroker broker, AsyncContext async) throws IOException {
		stream = broker.createStream();
		ServletInputStream in = req.getInputStream();
		in.setReadListener(new ReadListener() {

			@Override
			public final void onError(Throwable cause) {
				stream.sendError(cause);
			}

			@Override
			public final void onDataAvailable() throws IOException {
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
			}

			@Override
			public final void onAllDataRead() throws IOException {
				stream.sendClose();
			}

		});
	}

	// --- MULTIPART POST ---

	protected void createMultipartStream(ServiceBroker broker, AsyncContext async) throws IOException {
		stream = broker.createStream();
		MultipartContext context = new MultipartContext(contentType, contentLength, null);
		ParserListener listener = new ParserListener(stream, context);
		NioMultipartParser parser = Multipart.multipart(context).forNIO(listener);
		listener.setParser(parser);
		async.addListener(new AsyncListener() {

			@Override
			public final void onTimeout(AsyncEvent event) throws IOException {
				parser.close();
			}

			@Override
			public final void onError(AsyncEvent event) throws IOException {
				parser.close();
			}

			@Override
			public final void onComplete(AsyncEvent event) throws IOException {
				parser.close();
			}

			@Override
			public final void onStartAsync(AsyncEvent event) throws IOException {

				// Do nothing
			}

		});
		ServletInputStream in = req.getInputStream();
		in.setReadListener(new ReadListener() {

			@Override
			public final void onDataAvailable() throws IOException {
				int len = in.available();
				if (len < 1024 || len > 1048576) {
					len = 2048;
				}
				final byte buffer[] = new byte[len];
				while (in.isReady() && (len = in.read(buffer)) != -1) {
					if (len > 0) {
						parser.write(buffer, 0, len);
					}
				}
			}

			@Override
			public final void onError(Throwable t) {
				t.printStackTrace();
				try {
					stream.sendError(t);
				} catch (Exception ignored) {

					// Do nothing
				}
				try {
					parser.close();
				} catch (IOException ignored) {

					// Do nothing
				}
			}

			@Override
			public final void onAllDataRead() throws IOException {
				// Do nothing
			}

		});
	}

}