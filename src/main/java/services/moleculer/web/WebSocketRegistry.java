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
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import services.moleculer.ServiceBroker;
import services.moleculer.web.common.Endpoint;

public abstract class WebSocketRegistry implements Runnable {

	protected WebSocketFilter webSocketFilter;
	
	protected HashMap<String, HashSet<Endpoint>> registry = new HashMap<>();

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
		HashSet<Endpoint> endpoints;
		readLock.lock();
		try {
			endpoints = registry.get(path);
		} finally {
			readLock.unlock();
		}
		if (endpoints == null) {
			writeLock.lock();
			try {
				endpoints = new HashSet<Endpoint>();
				HashSet<Endpoint> prev = registry.put(path, endpoints);
				if (prev != null) {
					endpoints = prev;
				}
			} finally {
				writeLock.unlock();
			}
		}
		synchronized (endpoints) {
			endpoints.add(endpoint);
		}
	}

	public void deregister(String path, Endpoint endpoint) {
		HashSet<Endpoint> endpoints;
		readLock.lock();
		try {
			endpoints = registry.get(path);
		} finally {
			readLock.unlock();
		}
		if (endpoints == null) {
			return;
		}
		synchronized (endpoints) {
			endpoints.remove(endpoint);
		}
	}

	public void send(String path, String message) {
		HashSet<Endpoint> endpoints;
		readLock.lock();
		try {
			endpoints = registry.get(path);
		} finally {
			readLock.unlock();
		}
		if (endpoints == null || endpoints.isEmpty()) {
			return;
		}
		HashSet<Endpoint> snapshot;
		synchronized (endpoints) {
			if (endpoints.isEmpty()) {
				return;
			}
			snapshot = new HashSet<>(endpoints);
		}
		for (Endpoint endpoint : snapshot) {
			endpoint.send(message);
		}
	}

	@Override
	public void run() {
		HashSet<String> emptyKeys = new HashSet<>();

		readLock.lock();
		try {
			for (Map.Entry<String, HashSet<Endpoint>> entries : registry.entrySet()) {
				HashSet<Endpoint> endpoints = entries.getValue();
				synchronized (endpoints) {
					Iterator<Endpoint> i = endpoints.iterator();
					while (i.hasNext()) {
						Endpoint endpoint = i.next();
						if (!endpoint.isOpen()) {
							i.remove();
						}
					}
					if (endpoints.isEmpty()) {
						emptyKeys.add(entries.getKey());
					}
				}
			}
		} finally {
			readLock.unlock();
		}

		if (emptyKeys.isEmpty()) {
			return;
		}

		writeLock.lock();
		try {
			for (String key : emptyKeys) {
				HashSet<Endpoint> endpoints = registry.get(key);
				if (endpoints == null || endpoints.isEmpty()) {
					registry.remove(key);
				}
			}
		} finally {
			writeLock.unlock();
		}
	}

	public void setWebSocketFilter(WebSocketFilter webSocketFilter) {
		this.webSocketFilter = webSocketFilter;
	}
	
}
