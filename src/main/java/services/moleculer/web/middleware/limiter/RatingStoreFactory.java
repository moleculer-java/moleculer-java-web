package services.moleculer.web.middleware.limiter;

import java.util.HashSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import services.moleculer.ServiceBroker;
import services.moleculer.service.Service;

public abstract class RatingStoreFactory extends Service {

	protected HashSet<ScheduledFuture<?>> futures = new HashSet<>();

	protected ScheduledExecutorService scheduler;

	@Override
	public void started(ServiceBroker broker) throws Exception {
		super.started(broker);
		scheduler = broker.getConfig().getScheduler();
	}

	public RatingStore createStore(long windowMillis) {
		RatingStore store = createStore();
		ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(store::reset, windowMillis, windowMillis,
				TimeUnit.MILLISECONDS);
		synchronized (futures) {
			futures.add(future);
		}
		return store;
	}

	@Override
	public void stopped() {
		super.stopped();
		synchronized (futures) {
			for (ScheduledFuture<?> future : futures) {
				if (future != null) {
					future.cancel(true);
				}
			}
			futures.clear();
		}
	}

	protected abstract RatingStore createStore();

}
