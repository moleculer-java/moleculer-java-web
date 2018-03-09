package services.moleculer.web.middleware.limiter;

public interface RatingStore {

	public long incrementAndGet(String address);
	
}