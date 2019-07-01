/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2019 Andras Berkes [andras.berkes@programmer.net]<br>
 * Based on Moleculer Framework for NodeJS [https://moleculer.services].
 * <br><br>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:<br>
 * <br>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.<br>
 * <br>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package services.moleculer.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import services.moleculer.ServiceBroker;
import services.moleculer.web.common.Endpoint;

public abstract class WebSocketRegistry implements Runnable {

	protected WebSocketFilter webSocketFilter;

	protected HashMap<String, EndpointSet> registry = new HashMap<>(128);

	protected final ReadLock readLock;
	protected final WriteLock writeLock;

	protected final ScheduledFuture<?> timer;

	public WebSocketRegistry(ServiceBroker broker, long cleanupSeconds) {
		ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
		readLock = lock.readLock();
		writeLock = lock.writeLock();
		timer = broker.getConfig().getScheduler().scheduleAtFixedRate(this, cleanupSeconds, cleanupSeconds,
				TimeUnit.SECONDS);
	}

	public void stopped() {
		if (timer != null && !timer.isCancelled()) {
			timer.cancel(false);
		}
	}

	public void register(String path, Endpoint endpoint) {
		EndpointSet endpoints;
		readLock.lock();
		try {
			endpoints = registry.get(path);
		} finally {
			readLock.unlock();
		}
		if (endpoints == null) {
			endpoints = new EndpointSet();
			writeLock.lock();
			try {
				EndpointSet prev = registry.put(path, endpoints);
				if (prev != null) {
					endpoints = prev;
				}
			} finally {
				writeLock.unlock();
			}
		}
		endpoints.add(endpoint);
	}

	public void deregister(String path, Endpoint endpoint) {
		EndpointSet endpoints;
		readLock.lock();
		try {
			endpoints = registry.get(path);
		} finally {
			readLock.unlock();
		}
		if (endpoints == null) {
			return;
		}
		endpoints.remove(endpoint);
	}

	public void send(String path, String message) {
		EndpointSet endpoints;
		readLock.lock();
		try {
			endpoints = registry.get(path);
		} finally {
			readLock.unlock();
		}
		if (endpoints == null) {
			return;
		}
		Set<Endpoint> snapshot = endpoints.get();
		if (snapshot == null) {
			return;
		}
		for (Endpoint endpoint : snapshot) {
			endpoint.send(message);
		}
	}

	@Override
	public void run() {
		readLock.lock();
		try {
			if (registry.isEmpty()) {
				return;
			}
			for (EndpointSet endpoints : registry.values()) {
				endpoints.cleanup();
			}
		} finally {
			readLock.unlock();
		}
		writeLock.lock();
		try {
			Iterator<EndpointSet> i = registry.values().iterator();
			while (i.hasNext()) {
				if (i.next().canRemove()) {
					i.remove();
				}
			}
		} finally {
			writeLock.unlock();
		}
	}

	public void setWebSocketFilter(WebSocketFilter webSocketFilter) {
		this.webSocketFilter = webSocketFilter;
	}

	private class EndpointSet {

		private final AtomicBoolean empty = new AtomicBoolean();		
		private final AtomicLong lastTouched = new AtomicLong(); 
		
		private final ReadLock endpointReadLock;
		private final WriteLock endpointWriteLock;

		private final HashSet<Endpoint> set = new HashSet<>(128);
		
		private EndpointSet() {
			ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
			endpointReadLock = lock.readLock();
			endpointWriteLock = lock.writeLock();
		}

		private Set<Endpoint> get() {
			lastTouched.set(System.currentTimeMillis());			
			endpointReadLock.lock();
			try {
				if (set.isEmpty()) {
					return null;
				}
				return new HashSet<>(set);
			} finally {
				endpointReadLock.unlock();
			}
		}

		private void add(Endpoint endpoint) {
			lastTouched.set(System.currentTimeMillis());
			empty.set(false);
			endpointWriteLock.lock();
			try {
				set.add(endpoint);
			} finally {
				endpointWriteLock.unlock();
			}
		}

		private void remove(Endpoint endpoint) {
			lastTouched.set(System.currentTimeMillis());			
			endpointWriteLock.lock();
			try {
				if (set.remove(endpoint)) {
					empty.set(set.isEmpty());
				}
			} finally {
				endpointWriteLock.unlock();
			}
		}

		private boolean canRemove() {
			long now = System.currentTimeMillis();
			return empty.get() && now - lastTouched.get() > 60000;
		}

		private void cleanup() {
			endpointWriteLock.lock();
			try {
				Iterator<Endpoint> i = set.iterator();
				while (i.hasNext()) {
					if (!i.next().isOpen()) {
						i.remove();
					}
				}
				empty.set(set.isEmpty());
			} finally {
				endpointWriteLock.unlock();
			}
		}

	}

}