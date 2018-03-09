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