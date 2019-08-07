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

import java.io.IOException;
import java.util.LinkedList;
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

	protected final LinkedList<byte[]> queue = new LinkedList<>();

	// --- CONSTRUCTOR ---

	public NonBlockingWebResponse(AsyncContext async, HttpServletResponse rsp) throws IOException {
		super(rsp);
		this.async = async;
		out.setWriteListener(new WriteListener() {

			@Override
			public void onWritePossible() throws IOException {
				sendPacket();
			}

			@Override
			public void onError(Throwable t) {
				error.set(t);
			}

		});
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
		addToQueue(bytes);
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
				addToQueue(END_MARKER);
			} catch (Throwable t) {
				error.set(t);
			}
			return true;
		}
		return false;
	}

	// --- QUEUE UTILS ---

	protected void addToQueue(byte[] bytes) throws IOException {
		synchronized (queue) {
			queue.addLast(bytes);
		}
		sendPacket();
	}

	protected void sendPacket() throws IOException {
		synchronized (queue) {
			byte[] bytes;
			while (out.isReady() && !queue.isEmpty()) {
				bytes = queue.removeFirst();
				if (bytes == END_MARKER) {
					try {			
						out.close();
					} catch (Exception ignored) {
					} finally {
						async.complete();
					}
					return;
				}
				out.write(bytes);
			}
		}
	}

}