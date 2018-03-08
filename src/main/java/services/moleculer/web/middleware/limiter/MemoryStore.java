package services.moleculer.web.middleware.limiter;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MemoryStore implements RatingStore {
	
	protected HashMap<String, AtomicLong> hits = new HashMap<String, AtomicLong>();
	
	public long inc(String address) {
		AtomicLong counter;
		synchronized (hits) {
			counter = hits.get(address);
			if (counter == null) {
				counter = new AtomicLong();
				hits.put(address, counter);				
			}
		}
		return counter.incrementAndGet();
	}
	
	public void reset() {
		synchronized (hits) {
			hits.clear();
		}
	}
	
}