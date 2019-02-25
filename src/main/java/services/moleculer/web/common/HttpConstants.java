/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2017 Andras Berkes [andras.berkes@programmer.net]<br>
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
package services.moleculer.web.common;

public interface HttpConstants {

	// --- HTTP METHODS ---
	
	public static final String GET = "GET";
	public static final String HEAD = "HEAD";
	public static final String POST = "POST";
	public static final String PUT = "PUT";
	public static final String DELETE = "DELETE";
	public static final String CONNECT = "CONNECT";
	public static final String OPTIONS = "OPTIONS";
	public static final String TRACE = "TRACE";
	public static final String PATCH = "PATCH";
	
	// --- HEADER NAMES ---

	public static final String X_FORWARDED_FOR = "X-Forwarded-For";
	public static final String COOKIE = "Cookie";
	public static final String IF_NONE_MATCH = "If-None-Match";
	public static final String CONTENT_TYPE = "Content-Type";
	public static final String CONTENT_LENGTH = "Content-Length";
	public static final String CONNECTION = "Connection";
	public static final String ACCEPT_ENCODING = "Accept-Encoding";
	public static final String CONTENT_ENCODING = "Content-Encoding";
	public static final String ETAG = "ETag";
	public static final String SET_COOKIE = "Set-Cookie";
	public static final String CACHE_CONTROL = "Cache-Control";
	public static final String LOCATION = "Location";
	public static final String ACCEPT = "Accept";
	public static final String AUTHORIZATION = "Authorization";
	public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
	public static final String TRANSFER_ENCODING = "Transfer-Encoding";

	// --- HTTP HEADER VALUES ---

	public static final String GZIP = "gzip";
	public static final String DEFLATE = "deflate";
	public static final String KEEP_ALIVE = "keep-alive";
	public static final String CLOSE = "close";
	public static final String NO_CACHE = "no-store, no-cache, must-revalidate";
	public static final String CHUNKED = "chunked";
	
	// --- CONTENT TYPES ---

	public static final String CONTENT_TYPE_JSON = "application/json;charset=utf-8";
	public static final String CONTENT_TYPE_HTML = "text/html;charset=utf-8";

	// --- PROPERTY NAMES ---
	
	public static final String PROPERTY_SESSION_ID = "sid";
	public static final String PROPERTY_COOKIES = "cookies";
	public static final String PROPERTY_USER = "user";
	
	// --- SPECIAL VALUES IN META ---

	/**
	 * Name of the HTML template.
	 */
	public static final String META_TEMPLATE = "$template";

	/**
	 * Status code (eg. 200, 404) of the HTTP response message.
	 */
	public static final String META_STATUS = "$statusCode";
	
	/**
	 * Content-Type header's value of the HTTP response message.
	 */
	public static final String META_CONTENT_TYPE = "$responseType";
	
	/**
	 * Set of response headers.
	 */
	public static final String META_HEADERS = "$responseHeaders";
	
	/**
	 * Location in header for redirects.
	 */
	public static final String META_LOCATION = "$location";
	
}