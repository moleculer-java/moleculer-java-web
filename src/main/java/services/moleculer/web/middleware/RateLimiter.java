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

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;
import services.moleculer.web.middleware.limiter.MemoryStoreFactory;
import services.moleculer.web.middleware.limiter.RatingStore;
import services.moleculer.web.middleware.limiter.RatingStoreFactory;

/**
 * Rate Limiter limits concurrent constant requests to the HTTP calls in the
 * application. Sample:
 * <pre>
 * route.use(new RateLimiter(100, true));
 * </pre>
 */
@Name("Rate Limiter")
public class RateLimiter extends HttpMiddleware implements HttpConstants {

	// --- HTTP HEADERS ---

	public static final String X_HEADER_LIMIT = "X-Rate-Limit-Limit";
	public static final String X_HEADER_REMAINING = "X-Rate-Limit-Remaining";
	public static final String X_HEADER_RESET = "X-Rate-Limit-Reset";

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

	// --- CREATE NEW PROCESSOR ---

	@Override
	public RequestProcessor install(RequestProcessor next, Tree config) {

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

		// Convert to String
		String actionLimitString = Long.toString(actionLimit);
		String windowSecString = Long.toString(windowSec);

		// Check value of limit
		if (actionLimit < 1) {
			throw new IllegalArgumentException("Zero or negative \"rateLimit\" (" + actionLimit + ")!");
		}

		// Create new middleware-layer
		return new AbstractRequestProcessor(next) {

			private RatingStore store = storeFactory.createStore(windowMillis);

			/**
			 * Handles request of the HTTP client.
			 * 
			 * @param req
			 *            WebRequest object that contains the request the client
			 *            made of the ApiGateway
			 * @param rsp
			 *            WebResponse object that contains the response the
			 *            ApiGateway returns to the client
			 * 
			 * @throws Exception
			 *             if an input or output error occurs while the
			 *             ApiGateway is handling the HTTP request
			 */
			@Override
			public void service(WebRequest req, WebResponse rsp) throws Exception {

				// Get remote address
				String address = req.getHeader(X_FORWARDED_FOR);
				if (address == null || address.isEmpty()) {
					address = req.getAddress();
				}

				// Calculate remaining number of requests
				long remaining = actionLimit - store.incrementAndGet(address);
				if (remaining <= 0) {

					// Reject request, the limit is reached
					// 429 = Rate limit exceeded
					try {
						rsp.setStatus(429);
						rsp.setHeader(CONTENT_LENGTH, "0");

						// Set outgoing headers
						if (headers) {
							rsp.setHeader(X_HEADER_LIMIT, actionLimitString);
							rsp.setHeader(X_HEADER_REMAINING, "0");
							rsp.setHeader(X_HEADER_RESET, windowSecString);
						}
					} finally {

						// Response finished
						rsp.end();
					}
					return;
				}

				// Set outgoing headers
				if (headers) {
					rsp.setHeader(X_HEADER_LIMIT, actionLimitString);
					rsp.setHeader(X_HEADER_REMAINING, Long.toString(remaining));
					rsp.setHeader(X_HEADER_RESET, windowSecString);
				}

				// Invoke next handler (eg. Moleculer Action)
				next.service(req, rsp);
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