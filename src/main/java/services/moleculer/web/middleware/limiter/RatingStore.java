package services.moleculer.web.middleware.limiter;

public interface RatingStore {

	public long inc(String address);
	
	public void reset();
	
}
