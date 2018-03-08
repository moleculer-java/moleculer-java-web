package services.moleculer.web.middleware;

import io.datatree.Tree;
import services.moleculer.Promise;
import services.moleculer.context.Context;
import services.moleculer.service.Action;
import services.moleculer.service.Middleware;
import services.moleculer.service.Name;
import services.moleculer.web.common.HttpConstants;
import services.moleculer.web.middleware.limiter.MemoryStore;
import services.moleculer.web.middleware.limiter.RatingStore;

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
	protected int rateLimit;

	/**
	 * Store of invocation history
	 */
	protected RatingStore store = new MemoryStore();
	
	// --- CONSTRUCTORS ---

	public RateLimiter() {
	}
	
	public RateLimiter(int rateLimit) {
		this(rateLimit, true);
	}

	public RateLimiter(int rateLimit, boolean applyForAll) {
		this.rateLimit = rateLimit;
		this.applyForAll = applyForAll;
	}

	// --- CREATE NEW ACTION ---

	public Action install(Action action, Tree config) {

		// Check annotation
		int actionLimit = config.get("rateLimit", -1);
		if (actionLimit < 1) {
			if (!applyForAll) {

				// Do not limit the workload
				return null;
			}
			actionLimit = rateLimit;
		}

		// Check value of limit
		if (actionLimit < 1) {
			throw new IllegalArgumentException("Zero or negative \"rateLimit\" (" + actionLimit + ")!");
		}

		// Create new middleware-layer
		return new Action() {

			@Override
			public Object handler(Context ctx) throws Exception {

				// Invoke action
				Object result = action.handler(ctx);

				// Set outgoing header
				return Promise.resolve(result).then(rsp -> {

					Tree headers = rsp.getMeta().putMap(HEADERS, true);

				});
			}

		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public boolean isApplyForAll() {
		return applyForAll;
	}

	public void setApplyForAll(boolean applyForAll) {
		this.applyForAll = applyForAll;
	}

	public int getRateLimit() {
		return rateLimit;
	}

	public void setRateLimit(int rateLimit) {
		this.rateLimit = rateLimit;
	}

}