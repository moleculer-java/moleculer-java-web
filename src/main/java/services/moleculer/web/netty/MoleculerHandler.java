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

import static services.moleculer.web.common.GatewayUtils.sendError;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import services.moleculer.ServiceBroker;
import services.moleculer.web.ApiGateway;

public class MoleculerHandler extends SimpleChannelInboundHandler<Object> {

	// --- CHANNEL VARIABLES ---
	
	protected ApiGateway gateway;
	protected ServiceBroker broker;

	// --- PROCESSING VARIABLES ---
	
	protected volatile NettyWebRequest req;
	
	// --- WEBSOCKET VARIABLES ---

	protected volatile String path;	
	protected volatile WebSocketServerHandshaker handshaker;
	
	// --- CONSTRUCTOR ---

	public MoleculerHandler(ApiGateway gateway, ServiceBroker broker) {
		this.gateway = gateway;
		this.broker = broker;
	}
	
	// --- PROCESS INCOMING HTTP REQUEST ---

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object request) throws Exception {
		try {
			
			// --- HTTP MESSAGES ---
			
			// HTTP request -> begin
			if (request instanceof HttpRequest) {
				HttpRequest httpRequest = (HttpRequest) request;
				
				// Get URI + QueryString
				path = httpRequest.uri();

				// Get HTTP headers
				HttpHeaders httpHeaders = httpRequest.headers();

				// Upgrade to WebSocket connection
				if (httpHeaders.contains("Upgrade")) {
					WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(path, null,
							true);
					handshaker = factory.newHandshaker(httpRequest);
					if (handshaker == null) {
						WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
					} else {
						handshaker.handshake(ctx.channel(), httpRequest);
					}
					return;
				}

				// Normal HTTP message
				req = new NettyWebRequest(ctx, httpRequest, httpHeaders, broker, path);
				gateway.service(req, new NettyWebResponse(ctx, req));
				return;
			}
			
			// HTTP request -> content
			if (request instanceof HttpContent) {
				HttpContent content = (HttpContent) request;
				byte[] data = null;
				ByteBuf byteBuffer = content.content();
				if (byteBuffer == null) {
					return;
				}
				int len = byteBuffer.readableBytes();
				if (len < 1) {
					return;
				}
				data = new byte[len];
				byteBuffer.readBytes(data);							
				
				// Push data into the stream
				if (req.parser == null) {
					req.stream.sendData(data);
					if (request instanceof LastHttpContent) {
						req.stream.sendClose();
					}					
				} else {
					req.parser.write(data);
				}				
				return;
			}
			
			// --- WEBSOCKET MESSAGES ---
			
			// Process close/ping/continue WebSocket frames
			if (request instanceof CloseWebSocketFrame) {
				handshaker.close(ctx.channel(), ((CloseWebSocketFrame) request).retain());
				return;
			}
			if (request instanceof PingWebSocketFrame) {
				ctx.channel()
						.writeAndFlush(new PongWebSocketFrame(((PingWebSocketFrame) request).content().retain()));
				return;
			}
			if (request instanceof ContinuationWebSocketFrame) {
				return;
			}

			// Process WebSocket message frame
			if (request instanceof WebSocketFrame) {
				
				// Not implemented (use REST)	
				return;				
			}

			// Unknown package type
			throw new IllegalStateException("Unknown package type: " + request);

		} catch (Throwable cause) {
			sendError(new NettyWebResponse(ctx, req), cause);
			broker.getLogger(MoleculerHandler.class).error("Unable to process request!", cause);
		}
	}

}