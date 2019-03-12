/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2018 Andras Berkes [andras.berkes@programmer.net]<br>
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
package services.moleculer.web.netty;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import services.moleculer.ServiceBroker;
import services.moleculer.web.WebSocketRegistry;

public class NettyWebSocketRegistry implements WebSocketRegistry, Runnable {

	protected HashMap<String, WeakHashMap<ChannelHandlerContext, Long>> registry = new HashMap<>();

	protected final ScheduledFuture<?> timer;

	public NettyWebSocketRegistry(ServiceBroker broker, long cleanupSeconds) {
		timer = broker.getConfig().getScheduler().scheduleAtFixedRate(this, cleanupSeconds, cleanupSeconds,
				TimeUnit.SECONDS);
	}

	public void stopped() {
		if (timer != null && !timer.isCancelled()) {
			timer.cancel(false);			
		}
	}
	
	public void register(String path, ChannelHandlerContext ctx) {
		WeakHashMap<ChannelHandlerContext, Long> ctxs = registry.get(path);
		if (ctxs == null) {
			ctxs = new WeakHashMap<ChannelHandlerContext, Long>();
			registry.put(path, ctxs);
		}
		ctxs.put(ctx, System.currentTimeMillis());
	}

	public void deregister(String path, ChannelHandlerContext ctx) {
		WeakHashMap<ChannelHandlerContext, Long> ctxs = registry.get(path);
		if (ctxs == null) {
			return;
		}
		ctxs.remove(ctx);
	}
	
	@Override
	public void send(String path, String message) {
		WeakHashMap<ChannelHandlerContext, Long> ctxs = registry.get(path);
		if (ctxs == null || ctxs.isEmpty()) {
			return;
		}
		byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
		Iterator<ChannelHandlerContext> i = ctxs.keySet().iterator();
		ChannelHandlerContext ctx;
		while (i.hasNext()) {
			ctx = i.next();
			if (ctx == null) {
				continue;
			}
			ctx.write(new TextWebSocketFrame(Unpooled.wrappedBuffer(bytes)));
			ctx.flush();
		}
	}

	@Override
	public void run() {
		Iterator<WeakHashMap<ChannelHandlerContext, Long>> j = registry.values().iterator();
		WeakHashMap<ChannelHandlerContext, Long> ctxs;
		Iterator<ChannelHandlerContext> i;
		ChannelHandlerContext ctx;
		while (j.hasNext()) {
			ctxs = j.next();
			if (ctxs == null) {
				j.remove();
				continue;
			}
			i = ctxs.keySet().iterator();
			while (i.hasNext()) {
				ctx = i.next();
				if (ctx == null || ctx.channel() == null || !ctx.channel().isOpen()) {
					i.remove();
					continue;
				}
			}
			if (ctxs.isEmpty()) {
				j.remove();
			}
		}
	}

}