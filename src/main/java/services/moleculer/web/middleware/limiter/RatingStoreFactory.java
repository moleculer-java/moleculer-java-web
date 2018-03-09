package services.moleculer.web.middleware.limiter;

import services.moleculer.service.Service;

public abstract class RatingStoreFactory extends Service {

	public abstract RatingStore createStore(long windowMillis);

}
