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
package services.moleculer.web.netty;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import services.moleculer.ServiceBroker;
import services.moleculer.web.WebSocketRegistry;
import services.moleculer.web.common.Endpoint;

public class NettyWebSocketRegistry extends WebSocketRegistry {

	// --- LOGGER ---

	private static final Logger logger = LoggerFactory.getLogger(NettyWebSocketRegistry.class);

	// --- CONSTRUCTOR ---

	public NettyWebSocketRegistry(ServiceBroker broker, long cleanupSeconds) {
		super(broker, cleanupSeconds);
	}

	public void register(String path, ChannelHandlerContext ctx) {
		register(path, toEnpoint(ctx));
	}

	public void deregister(String path, ChannelHandlerContext ctx) {
		deregister(path, toEnpoint(ctx));
	}

	protected Endpoint toEnpoint(ChannelHandlerContext ctx) {
		return new Endpoint() {

			@Override
			public final void send(String message) {
				ctx.write(message);
				ctx.flush();
			}

			@Override
			public final boolean isOpen() {
				return ctx.channel() != null && ctx.channel().isOpen();
			}

			@Override
			public int hashCode() {
				return ctx.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == null || !(obj instanceof Endpoint)) {
					return false;
				}
				Endpoint e = (Endpoint) obj;
				return e.getInternal() == ctx;
			}

			@Override
			public Object getInternal() {
				return ctx;
			}
			
		};
	}

	// --- CHECK ACCESS ---

	public boolean isRefused(ChannelHandlerContext ctx, HttpRequest req, HttpHeaders headers, ServiceBroker broker,
			String path) throws IOException {
		if (webSocketFilter == null) {
			return false;
		}
		NettyWebRequest webRequest = new NettyWebRequest(ctx, req, headers, broker, path);
		boolean accept = webSocketFilter.onConnect(webRequest);
		if (accept) {
			return false;
		}
		ctx.close();
		logger.info("Inbound WebSocket connection closed due to rejection of the WebSocket Filter: " + ctx);
		return true;
	}

}