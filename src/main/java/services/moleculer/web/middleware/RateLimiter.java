package services.moleculer.web.middleware;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.datatree.Tree;
import services.moleculer.Promise;
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
	protected long limit = 30;

	/**
	 * 
	 */
	protected long window = 1;
	
	/**
	 * 
	 */
	protected boolean headers = true;
	
	/**
	 * 
	 */
	protected TimeUnit unit = TimeUnit.MINUTES;

	/**
	 * 
	 */
	protected RatingStoreFactory storeFactory = new MemoryStoreFactory();
	
	// --- CONSTRUCTORS ---

	public RateLimiter() {
		this(30, 1, TimeUnit.MINUTES, true);
	}
	
	public RateLimiter(int rateLimit) {
		this(rateLimit, 1, TimeUnit.MINUTES, true);
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
		String windowSec = Long.toString(actionUnit.toSeconds(actionWindow));
		
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
				//String address = reqMeta.get("sessionID", "");
				String address = "icebob";
				
				long remaining = actionLimit - store.inc(address); 
				
				if (remaining <= 0) {
					
					 // Reject request, the limit is reached
					 Tree out = new Tree();
					 Tree meta = out.getMeta();
					 meta.put(STATUS, 429); // 429 - Rate limit exceeded
					 
					 Tree metaHeaders = meta.putMap(HEADERS, true);
					 
					 if (headers) {
						 metaHeaders.put("X-Rate-Limit-Limit", actionLimit);
						 metaHeaders.put("X-Rate-Limit-Remaining", "0");
						 metaHeaders.put("X-Rate-Limit-Reset", windowSec);
					 }					 
					 return out;					
				}
				
				
				// Invoke action
				Object result = action.handler(ctx);

				// Set outgoing headers and statuses
				return Promise.resolve(result).then(rsp -> {
					if (headers) {
						
						// Get response meta
						Tree rspMeta = rsp.getMeta();
						
						// Get response headers
						Tree metaHeaders = rspMeta.putMap(HEADERS, true);

						metaHeaders.put("X-Rate-Limit-Limit", actionLimit);
						metaHeaders.put("X-Rate-Limit-Remaining", remaining);
						metaHeaders.put("X-Rate-Limit-Reset", windowSec);
					}
				});
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
		this.unit = Objects.requireNonNull(unit);;
	}

	public RatingStoreFactory getStoreFactory() {
		return storeFactory;
	}

	public void setStoreFactory(RatingStoreFactory storeFactory) {
		this.storeFactory = Objects.requireNonNull(storeFactory);
	}
	
}