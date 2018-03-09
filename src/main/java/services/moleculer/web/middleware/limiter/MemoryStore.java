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
package services.moleculer.web.middleware.limiter;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MemoryStore implements RatingStore {

	protected final long windowMillis;

	protected final HashMap<String, AtomicLong> hits = new HashMap<String, AtomicLong>();

	protected volatile long lastCleared;

	public MemoryStore(long windowMillis) {
		this.windowMillis = windowMillis;
	}

	public long incrementAndGet(String address) {
		long now = System.currentTimeMillis();
		AtomicLong counter;
		synchronized (hits) {
			if (lastCleared + windowMillis < now) {
				lastCleared = now;
				hits.clear();
				counter = new AtomicLong();
				hits.put(address, counter);
			} else {
				counter = hits.get(address);
				if (counter == null) {
					counter = new AtomicLong();
					hits.put(address, counter);
				}
			}
		}
		return counter.incrementAndGet();
	}

}