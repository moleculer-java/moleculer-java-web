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
import java.net.SocketAddress;
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

	public NettyWebRequest(ChannelHandlerContext ctx, HttpRequest req, HttpHeaders headers, ServiceBroker broker,
			String path) throws IOException {
		this.ctx = ctx;
		this.headers = headers;

		// Get method
		method = req.method().name();

		// Get content type
		contentType = headers.get("Content-Type");

		// Get QueryString
		boolean isConnect = "CONNECT".equals(method);
		if (isConnect) {
			this.query = null;
			this.path = "/";
		} else {
			int i = path.indexOf('?');
			if (i > -1) {
				this.query = path.substring(i + 1);
				this.path = path.substring(0, i);
			} else {
				this.query = null;
				this.path = path;
			}
		}

		// Has body?
		if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method) || "TRACE".equals(method) || isConnect) {

			// Not a stream
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

	/**
	 * Returns the Internet Protocol (IP) address of the client or last proxy
	 * that sent the request. For HTTP servlets, same as the value of the CGI
	 * variable REMOTE_ADDR.
	 * 
	 * @return a String containing the IP address of the client that sent the
	 *         request
	 */
	@Override
	public String getAddress() {
		SocketAddress address = ctx.channel().remoteAddress();
		if (address == null) {
			return "0.0.0.0";
		}
		return ((InetSocketAddress) address).getHostString();
	}

	/**
	 * Returns the name of the HTTP method with which this request was made, for
	 * example, GET, POST, or PUT. Same as the value of the CGI variable
	 * REQUEST_METHOD.
	 * 
	 * @return a String specifying the name of the method with which this
	 *         request was made
	 */
	@Override
	public String getMethod() {
		return method;
	}

	/**
	 * Returns any extra path information associated with the URL the client
	 * sent when it made this request. The extra path information follows the
	 * servlet path but precedes the query string and will start with a "/"
	 * character.
	 * 
	 * @return a String, decoded by the web container, specifying extra path
	 *         information that comes after the servlet path but before the
	 *         query string in the request URL
	 */
	@Override
	public String getPath() {
		return path;
	}

	/**
	 * Returns the query string that is contained in the request URL after the
	 * path. This method returns null if the URL does not have a query string.
	 * Same as the value of the CGI variable QUERY_STRING.
	 * 
	 * @return a String containing the query string or null if the URL contains
	 *         no query string. The value is not decoded by the container
	 */
	@Override
	public String getQuery() {
		return query;
	}

	/**
	 * Returns the length, in bytes, of the request body and made available by
	 * the input stream, or -1 if the length is not known ir is greater than
	 * Integer.MAX_VALUE. For HTTP servlets, same as the value of the CGI
	 * variable CONTENT_LENGTH.
	 * 
	 * @return an integer containing the length of the request body or -1 if the
	 *         length is not known or is greater than Integer.MAX_VALUE
	 */
	@Override
	public int getContentLength() {
		return contentLength;
	}

	/**
	 * Returns the MIME type of the body of the request, or null if the type is
	 * not known. For HTTP servlets, same as the value of the CGI variable
	 * CONTENT_TYPE.
	 * 
	 * @return a String containing the name of the MIME type of the request, or
	 *         null if the type is not known
	 */
	@Override
	public String getContentType() {
		return contentType;
	}

	/**
	 * Returns the request body as PacketStream.
	 * 
	 * @return Request body (or null)
	 */
	@Override
	public PacketStream getBody() {
		return stream;
	}

	/**
	 * Returns the value of the specified request header as a String. If the
	 * request did not include a header of the specified name, this method
	 * returns null. If there are multiple headers with the same name, this
	 * method returns the first head in the request. The header name is case
	 * insensitive. You can use this method with any request header.
	 * 
	 * @param name
	 *            name a String specifying the header name
	 * 
	 * @return a String containing the value of the requested header, or null if
	 *         the request does not have a header of that name
	 */
	@Override
	public String getHeader(String name) {
		return headers.get(name);
	}

	/**
	 * Returns an iterator of all the header names this request contains. If the
	 * request has no headers, this method returns an empty iterator.
	 * 
	 * @return an iterator of all the header names sent with this request; if
	 *         the request has no headers, an empty iterator
	 */
	@Override
	public Iterator<String> getHeaders() {
		return headers.names().iterator();
	}

	/**
	 * Checks if the Content-Type header defines a multipart request.
	 * 
	 * @return true if the request is a multipart request, false otherwise
	 */
	@Override
	public boolean isMultipart() {
		return multipart;
	}

	// --- ACCESS TO INTERNAL OBJECT ---
	
	/**
	 * Returns the internal object of this WebRequest.
	 * 
	 * @return internal object (Netty ChannelHandlerContext)
	 */
	@Override
	public Object getInternalObject() {
		return ctx;
	}
	
}