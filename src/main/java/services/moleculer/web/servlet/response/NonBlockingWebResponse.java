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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.AsyncContext;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

public class NonBlockingWebResponse extends AbstractWebResponse {

	// --- END MARKER ---

	protected static final byte[] END_MARKER = new byte[0];

	// --- RESPONSE VARIABLES ---

	protected final AtomicReference<Throwable> error = new AtomicReference<>();

	protected final AtomicBoolean listenerSet = new AtomicBoolean();

	protected final AtomicBoolean lock = new AtomicBoolean();

	protected final LinkedList<byte[]> queue = new LinkedList<>();

	protected final WriteListener listener;

	// --- CONSTRUCTOR ---

	public NonBlockingWebResponse(AsyncContext async) throws IOException {
		super((HttpServletResponse) async.getResponse());
		listener = new WriteListener() {

			@Override
			public void onWritePossible() throws IOException {
				while (out.isReady()) {
					if (lock.compareAndSet(false, true)) {
						try {
							if (queue.isEmpty()) {
								return;
							}
							byte[] bytes = queue.removeFirst();
							if (bytes == END_MARKER) {
								try {
									out.close();
								} catch (Throwable ignored) {
								}
								try {
									async.complete();
								} catch (Throwable ignored) {
								}
								return;
							}
							out.write(bytes);
						} catch (Throwable cause) {
							error.set(cause);
						} finally {
							lock.set(false);
						}
					}
				}
			}

			@Override
			public void onError(Throwable cause) {
				error.set(cause);
			}

		};
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
		Throwable cause = error.get();
		if (cause != null) {
			if (cause instanceof IOException) {
				throw (IOException) cause;
			}
			throw new IOException(cause);
		}
		addToQueueAndSend(bytes);
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
			addToQueueAndSend(END_MARKER);
			return true;
		}
		return false;
	}

	protected void addToQueueAndSend(byte[] bytes) {
		while (true) {
			if (lock.compareAndSet(false, true)) {
				try {
					queue.addLast(bytes);
					break;
				} finally {
					lock.set(false);
				}
			}
		}
		if (listenerSet.compareAndSet(false, true)) {
			out.setWriteListener(listener);
		}
		try {
			listener.onWritePossible();
		} catch (Throwable cause) {
			error.set(cause);
		}
	}

}