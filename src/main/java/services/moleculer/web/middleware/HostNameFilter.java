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
package services.moleculer.web.middleware;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import services.moleculer.ServiceBroker;
import services.moleculer.service.Name;
import services.moleculer.web.WebRequest;

/**
 * The HostNameFilter adds the ability to allow or block requests based on
 * the host name of the client. Sample:
 * <pre>
 * HostNameFilter filter = new HostNameFilter();
 * filter.allow("domain.server**");
 * route.use(filter);
 * </pre>
 */
@Name("Host Name Filter")
public class HostNameFilter extends IpFilter implements Runnable {

	// --- CACHE TIMEOUT PROPERTIES ---
	
	/**
	 * Cleanup period in SECONDS (default = 1 minute)
	 */
	protected int cleanup = 60;

	/**
	 * Cache entry timeout in SECONDS (default = 10 minute).
	 */
	protected int timeout = 60 * 10;
	
	// --- HOSTNAME CACHE ---
	
	protected ConcurrentHashMap<String, CachedHostname> cache = new ConcurrentHashMap<>(64);
	
	// --- TIMER ---
	
	protected ScheduledFuture<?> timer;
	
	// --- CONSTRUCTORS ---

	public HostNameFilter() {
	}

	public HostNameFilter(String... allow) {
		super(allow);
	}
	
	// --- GET HOSTNAME BY IP ---
	
	@Override
	protected String getAddress(WebRequest req) {
		try {
			
			// Get IP of the client
			String ip = req.getAddress();
			
			// Find hostname in cache
			CachedHostname entry = cache.get(ip);
			
			// Found?
			if (entry == null) {
				
				// Find canonical hostname by IP
				InetAddress address = InetAddress.getByName(ip);
				entry = new CachedHostname(address.getCanonicalHostName());
				
				// Store in cache
				cache.put(ip, entry);
			}
			return entry.hostname;
		} catch (Exception cause) {
			logger.warn("Unable to detect host name of client!", cause);
		}
		return null;
	}

	// --- START CLEANUP PROCESS ---
	
	@Override
	public void started(ServiceBroker broker) throws Exception {
		super.started(broker);
		
		// Start timer
		ScheduledExecutorService scheduler = broker.getConfig().getScheduler();
		timer = scheduler.scheduleAtFixedRate(this, cleanup, cleanup, TimeUnit.SECONDS);
	}

	// --- STOP CLEANUP PROCESS ---
	
	@Override
	public void stopped() {
		super.stopped();
		
		// Stop timer
		if (timer != null) {
			timer.cancel(false);
			timer = null;
		}
	}
	
	// --- CLEANUP PROCESS ---
	
	@Override
	public void run() {
		Iterator<CachedHostname> entries = cache.values().iterator();
		CachedHostname entry;
		long timeoutMillis = 1000L * timeout;
		long now = System.currentTimeMillis();
		while (entries.hasNext()) {
			entry = entries.next();
			if (now - entry.timestamp > timeoutMillis) {
				entries.remove();
			}
		}
	}
	
	// --- CACHED HOSTNAME ---
	
	protected static class CachedHostname {
		
		public final long timestamp = System.currentTimeMillis();

		public final String hostname;

		protected CachedHostname(String hostname) {
			this.hostname = hostname;			
		}
		
	}
	
	// --- GETTERS / SETTERS ---

	/**
	 * @return the cleanup
	 */
	public int getCleanup() {
		return cleanup;
	}

	/**
	 * @param cleanup the cleanup to set
	 */
	public void setCleanup(int cleanup) {
		this.cleanup = cleanup;
	}

	/**
	 * @return the timeout
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * @param timeout the timeout to set
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
	
}