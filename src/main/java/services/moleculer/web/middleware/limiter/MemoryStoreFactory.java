package services.moleculer.web.middleware.limiter;

public class MemoryStoreFactory extends RatingStoreFactory {

	@Override
	public RatingStore createStore(long windowMillis) {
		return new MemoryStore(windowMillis);
	}

}
