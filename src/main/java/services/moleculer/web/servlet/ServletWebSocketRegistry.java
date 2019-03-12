package services.moleculer.web.servlet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.websocket.Session;

import services.moleculer.ServiceBroker;
import services.moleculer.web.WebSocketRegistry;

public class ServletWebSocketRegistry implements WebSocketRegistry, Runnable {

	protected HashMap<String, WeakHashMap<Session, Long>> registry = new HashMap<>();

	protected final ScheduledFuture<?> timer;
	
	public ServletWebSocketRegistry(ServiceBroker broker, long cleanupSeconds) {
		timer = broker.getConfig().getScheduler().scheduleAtFixedRate(this, cleanupSeconds, cleanupSeconds,
				TimeUnit.SECONDS);
	}
	
	public void stopped() {
		if (timer != null && !timer.isCancelled()) {
			timer.cancel(false);			
		}
	}
	
	public void register(Session session) {
		String path = session.getRequestURI().toString();
		WeakHashMap<Session, Long> sessions = registry.get(path);
		if (sessions == null) {
			sessions = new WeakHashMap<Session, Long>();
			registry.put(path, sessions);
		}
		sessions.put(session, System.currentTimeMillis());
	}

	public void deregister(Session session) {
		String path = session.getRequestURI().toString();
		WeakHashMap<Session, Long> sessions = registry.get(path);
		if (sessions == null) {
			return;
		}
		sessions.remove(session);
	}

	@Override
	public void send(String path, String message) {
		WeakHashMap<Session, Long> sessions = registry.get(path);
		if (sessions == null || sessions.isEmpty()) {
			return;
		}
		Iterator<Session> i = sessions.keySet().iterator();
		Session session;
		while (i.hasNext()) {
			session = i.next();
			if (session == null) {
				continue;
			}
			session.getAsyncRemote().sendText(message);
		}
	}

	@Override
	public void run() {
		Iterator<WeakHashMap<Session, Long>> j = registry.values().iterator();
		WeakHashMap<Session, Long> sessions;
		Iterator<Session> i;
		Session session;
		while (j.hasNext()) {
			sessions = j.next();
			if (sessions == null) {
				j.remove();
				continue;
			}
			i = sessions.keySet().iterator();
			while (i.hasNext()) {
				session = i.next();
				if (session == null || !session.isOpen()) {
					i.remove();
					continue;
				}
			}
			if (sessions.isEmpty()) {
				j.remove();
			}
		}
	}
	
}