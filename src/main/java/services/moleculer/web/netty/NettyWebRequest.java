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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;

import org.synchronoss.cloud.nio.multipart.Multipart;
import org.synchronoss.cloud.nio.multipart.MultipartContext;
import org.synchronoss.cloud.nio.multipart.MultipartUtils;
import org.synchronoss.cloud.nio.multipart.NioMultipartParser;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import services.moleculer.ServiceBroker;
import services.moleculer.stream.PacketStream;
import services.moleculer.web.WebRequest;
import services.moleculer.web.common.ParserListener;

public class NettyWebRequest implements WebRequest {

	// --- REQUEST VARIABLES ----
	
	protected final ChannelHandlerContext ctx;

	protected final int contentLength;

	protected final String contentType;
	
	protected final HttpHeaders headers;

	protected final String method;
	
	protected final String path;	
	
	protected final String query;
	
	protected final boolean multipart;
	
	// --- BODY PROCESSORS ---
	
	protected PacketStream stream;
	
	protected NioMultipartParser parser;
			
	// --- CONSTRUCTOR ---
	
	public NettyWebRequest(ChannelHandlerContext ctx, HttpRequest req, HttpHeaders headers, ServiceBroker broker, String path) throws IOException {
		this.ctx = ctx;
		this.headers = headers;
		
		// Get method
		method = req.method().name();
		
		// Get content type
		contentType = headers.get("Content-Type");
		
		// Get QueryString
		path = req.uri();
		int i = path.indexOf('?');
		if (i > -1) {
			query = path.substring(i + 1);
			this.path = path.substring(0, i);
		} else {
			query = null;
			this.path = path; 
		}
		
		// Has body?
		if (!"POST".equals(method) && !"PUT".equals(method)) {

			// Not POST or PUT -> not a stream
			multipart = false;
			contentLength = 0;
			return;
		}
		contentLength = headers.getInt("Content-Length", -1);
		if (contentLength == 0) {

			// Zero Content Length -> not a stream
			multipart = false;
			return;
		}
		
		// Create stream
		stream = broker.createStream();
		
		// Create body stream
		multipart = MultipartUtils.isMultipart(contentType);
		if (multipart) {
			MultipartContext context = new MultipartContext(contentType, contentLength, null);
			ParserListener listener = new ParserListener(stream, context);
			parser = Multipart.multipart(context).forNIO(listener);
			listener.setParser(parser);
		}
	}
	
	// --- PROPERTY GETTERS ---
	
	@Override
	public String getAddress() {
		return ((InetSocketAddress) ctx.channel().remoteAddress()).getHostString();
	}

	@Override
	public String getMethod() {
		return method;
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public String getQuery() {
		return query;
	}

	@Override
	public int getContentLength() {
		return contentLength;
	}

	@Override
	public String getContentType() {
		return contentType;
	}
	
	@Override
	public PacketStream getBody() {
		return stream;
	}

	@Override
	public String getHeader(String name) {
		return headers.get(name);
	}

	@Override
	public Iterator<String> getHeaders() {
		return headers.names().iterator();
	}
		
	@Override
	public boolean isMultipart() {
		return multipart;
	}
	
}