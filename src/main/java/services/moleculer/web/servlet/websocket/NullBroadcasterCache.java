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
package services.moleculer.web.servlet.websocket;

import java.util.Collections;
import java.util.List;

import org.atmosphere.cache.BroadcastMessage;
import org.atmosphere.cache.BroadcasterCacheInspector;
import org.atmosphere.cache.CacheMessage;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcasterCache;
import org.atmosphere.cpr.BroadcasterCacheListener;

public class NullBroadcasterCache implements BroadcasterCache {

	@Override
	public void configure(AtmosphereConfig config) {
		
		// Do nothing
	}

	@Override
	public void start() {
		
		// Do nothing
	}

	@Override
	public void stop() {
		
		// Do nothing
	}

	@Override
	public void cleanup() {
		
		// Do nothing
	}

	@Override
	public CacheMessage addToCache(String broadcasterId, String uuid, BroadcastMessage message) {
		return null;
	}

	@Override
	public List<Object> retrieveFromCache(String id, String uuid) {
		return Collections.emptyList();
	}

	@Override
	public BroadcasterCache clearCache(String broadcasterId, String uuid, CacheMessage cache) {
		return this;
	}

	@Override
	public BroadcasterCache excludeFromCache(String broadcasterId, AtmosphereResource r) {
		return this;
	}

	@Override
	public BroadcasterCache cacheCandidate(String broadcasterId, String uuid) {
		return this;
	}

	@Override
	public BroadcasterCache inspector(BroadcasterCacheInspector interceptor) {
		return this;
	}

	@Override
	public BroadcasterCache addBroadcasterCacheListener(BroadcasterCacheListener l) {
		return this;
	}

	@Override
	public BroadcasterCache removeBroadcasterCacheListener(BroadcasterCacheListener l) {
		return this;
	}

}
