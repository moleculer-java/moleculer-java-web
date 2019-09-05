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
package services.moleculer.web.servlet.response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.AsyncContext;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

public class NonBlockingWebResponse extends AbstractWebResponse {

	// --- CONSTANTS ---

	protected static final byte[] END_MARKER = new byte[0];

	// --- RESPONSE VARIABLES ---

	protected final AsyncContext async;

	protected final AtomicReference<Throwable> error = new AtomicReference<>();

	protected final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	protected final AtomicBoolean listenerSet = new AtomicBoolean();

	// --- CONSTRUCTOR ---

	public NonBlockingWebResponse(AsyncContext async) throws IOException {
		super((HttpServletResponse) async.getResponse());
		this.async = async;
	}

	// --- ASYNC WRITE ---

	/**
	 * Writes b.length bytes of body from the specified byte array to the output
	 * stream.
	 * 
	 * @param bytes
	 *            the data
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	public void send(byte[] bytes) throws IOException {
		Throwable t = error.get();
		if (t != null) {
			if (t instanceof IOException) {
				throw (IOException) t;
			}
			throw new IOException(t);
		}
		buffer.write(bytes, 0, bytes.length);
		tryToSend();
	}

	protected void tryToSend() throws IOException {
		byte[] bytes;
		boolean ready = out.isReady();
		synchronized (buffer) {
			bytes = buffer.toByteArray();
			if (ready) {
				buffer.reset();
			}
		}
		boolean empty = bytes.length == 0;
		if (ready && !empty) {
			
			// Send data
			out.write(bytes);			
		}
		if ((ready || empty) && closed.get()) {
			
			// Closed
			try {
				out.close();
			} finally {
				async.complete();
			}
			return;
		}
		if ((!ready || empty) && listenerSet.compareAndSet(false, true)) {
			
			// Send later
			out.setWriteListener(new SendLaterListener(async));
		}
	}

	// --- END PROCESSING ---

	/**
	 * Completes the synchronous operation that was started on the request.
	 * 
	 * @return return true, if any resources are released
	 */
	@Override
	public boolean end() {
		if (closed.compareAndSet(false, true)) {
			try {
				tryToSend();				
			} catch (Exception ignored) {
				ignored.printStackTrace();
			}
			return true;
		}
		return false;
	}

	// --- LISTENER ---

	protected class SendLaterListener implements WriteListener {

		private final AsyncContext ctx;

		private SendLaterListener(AsyncContext ctx) {
			this.ctx = ctx;
		}

		@Override
		public void onWritePossible() throws IOException {
			tryToSend();
		}

		@Override
		public void onError(Throwable t) {
			error.set(t);
		}

		@Override
		public int hashCode() {
			return async.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj != null && obj instanceof SendLaterListener) {
				return ((SendLaterListener) obj).ctx == ctx;
			}
			return false;
		}

	}

}