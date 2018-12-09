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

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.datatree.Promise;
import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.context.Context;
import services.moleculer.service.Action;
import services.moleculer.service.Middleware;
import services.moleculer.service.Name;
import services.moleculer.web.common.HttpConstants;
import services.moleculer.web.middleware.limiter.MemoryStoreFactory;
import services.moleculer.web.middleware.limiter.RatingStore;
import services.moleculer.web.middleware.limiter.RatingStoreFactory;

@Name("Rate Limiter")
public class RateLimiter extends Middleware implements HttpConstants {

	// --- PROPERTIES ---

	/**
	 * Apply the performance cutoff for each service (= true), otherwise (=
	 * false) it will only limit the load with the Actions marked with the
	 * annotation.
	 */
	protected boolean applyForAll;

	/**
	 * Default rate limit
	 */
	protected long limit = 50;

	/**
	 * Time "window" length
	 */
	protected long window = 1;

	/**
	 * Add headers to all HTTP response (eg. "X-Rate-Limit-Remaining", etc.)
	 */
	protected boolean headers = true;

	/**
	 * Unit of the time "window"
	 */
	protected TimeUnit unit = TimeUnit.SECONDS;

	/**
	 * Hits per IP addresses store
	 */
	protected RatingStoreFactory storeFactory = new MemoryStoreFactory();

	// --- CONSTRUCTORS ---

	public RateLimiter() {
		this(50, 1, TimeUnit.SECONDS, false);
	}

	public RateLimiter(int rateLimit, boolean applyForAll) {
		this(rateLimit, 1, TimeUnit.SECONDS, applyForAll);
	}

	public RateLimiter(int rateLimit, int window, TimeUnit unit, boolean applyForAll) {
		this.limit = rateLimit;
		this.window = window;
		this.unit = Objects.requireNonNull(unit);
		this.applyForAll = applyForAll;
	}

	// --- START INSTANCE ---

	@Override
	public void started(ServiceBroker broker) throws Exception {
		super.started(broker);
		storeFactory.started(broker);
	}

	// --- CREATE NEW ACTION ---

	public Action install(Action action, Tree config) {

		// Check annotation
		Tree rateLimit = config.get("rateLimit");

		// Properties
		long actionLimit;
		long actionWindow;
		TimeUnit actionUnit;

		// Get action's config values
		if (rateLimit == null) {
			if (applyForAll) {

				// Set to default values
				actionLimit = limit;
				actionWindow = window;
				actionUnit = unit;
			} else {

				// Do not limit the workload
				return null;
			}
		} else {
			actionLimit = rateLimit.get("value", limit);
			actionWindow = rateLimit.get("window", window);
			actionUnit = TimeUnit.valueOf(rateLimit.get("unit", unit.toString()));
		}

		// Convert to milliseconds
		long windowMillis = actionUnit.toMillis(actionWindow);

		// Convert to seconds
		long windowSec = actionUnit.toSeconds(actionWindow);

		// Check value of limit
		if (actionLimit < 1) {
			throw new IllegalArgumentException("Zero or negative \"rateLimit\" (" + actionLimit + ")!");
		}

		// Create new middleware-layer
		return new Action() {

			private RatingStore store = storeFactory.createStore(windowMillis);

			@Override
			public Object handler(Context ctx) throws Exception {

				// Get remote address
				Tree reqMeta = ctx.params.getMeta();
				Tree reqHeaders = reqMeta.get(HEADERS);
				String address = null;
				if (reqHeaders != null) {
					address = reqHeaders.get(REQ_X_FORWARDED_FOR, (String) null);
				}
				if (address == null) {
					address = reqMeta.get(ADDRESS, "");
				}

				// Calculate remaining number of requests
				long remaining = actionLimit - store.incrementAndGet(address);
				if (remaining <= 0) {

					// Reject request, the limit is reached
					Tree out = new Tree();
					Tree meta = out.getMeta();

					// 429 = Rate limit exceeded
					meta.put(STATUS, 429);

					if (headers) {
						Tree metaHeaders = meta.putMap(HEADERS, true);
						metaHeaders.put("X-Rate-Limit-Limit", actionLimit);
						metaHeaders.put("X-Rate-Limit-Remaining", 0);
						metaHeaders.put("X-Rate-Limit-Reset", windowSec);
					}
					return out;
				}

				// Invoke action
				Object result = action.handler(ctx);

				// Set outgoing headers
				if (headers) {
					return Promise.resolve(result).then(rsp -> {
						Tree metaHeaders = rsp.getMeta().putMap(HEADERS, true);
						metaHeaders.put("X-Rate-Limit-Limit", actionLimit);
						metaHeaders.put("X-Rate-Limit-Remaining", remaining);
						metaHeaders.put("X-Rate-Limit-Reset", windowSec);
					});
				} else {
					return result;
				}
			}
		};
	}

	// --- STOP INSTANCE ---

	@Override
	public void stopped() {
		super.stopped();
		if (storeFactory != null) {
			storeFactory.stopped();
		}
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public boolean isApplyForAll() {
		return applyForAll;
	}

	public void setApplyForAll(boolean applyForAll) {
		this.applyForAll = applyForAll;
	}

	public long getRateLimit() {
		return limit;
	}

	public void setRateLimit(long rateLimit) {
		this.limit = rateLimit;
	}

	public long getWindow() {
		return window;
	}

	public void setWindow(long window) {
		this.window = window;
	}

	public TimeUnit getUnit() {
		return unit;
	}

	public void setUnit(TimeUnit unit) {
		this.unit = Objects.requireNonNull(unit);
	}

	public RatingStoreFactory getStoreFactory() {
		return storeFactory;
	}

	public void setStoreFactory(RatingStoreFactory storeFactory) {
		this.storeFactory = Objects.requireNonNull(storeFactory);
	}

	public boolean isHeaders() {
		return headers;
	}

	public void setHeaders(boolean headers) {
		this.headers = headers;
	}

}