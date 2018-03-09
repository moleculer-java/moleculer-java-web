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

	// --- LOWERCASE REQUEST HEADERS ---

	public static final String REQ_CONTENT_TYPE = "content-type";
	public static final String REQ_CONTENT_LENGTH = "content-length";
	public static final String REQ_IF_NONE_MATCH = "if-none-match";
	public static final String REQ_CONNECTION = "connection";
	public static final String REQ_ACCEPT_ENCODING = "accept-encoding";
	public static final String REQ_CONTENT_ENCODING = "content-encoding";
	public static final String REQ_COOKIE = "cookie";
	public static final String REQ_X_FORWARDED_FOR = "x-forwarded-for";

	// --- RESPONSE HEADERS ---

	public static final String RSP_CONTENT_TYPE = "Content-Type";
	public static final String RSP_CONTENT_LENGTH = "Content-Length";
	public static final String RSP_CONNECTION = "Connection";
	public static final String RSP_ACCEPT_ENCODING = "Accept-Encoding";
	public static final String RSP_CONTENT_ENCODING = "Content-Encoding";
	public static final String RSP_ETAG = "ETag";
	public static final String RSP_SET_COOKIE = "Set-Cookie";
	public static final String RSP_CACHE_CONTROL = "Cache-Control";

	// --- HTTP HEADER VALUES ---

	public static final String DEFLATE = "deflate";
	public static final String KEEP_ALIVE = "keep-alive";
	public static final String CLOSE = "close";

	// --- META VARIABLES ---

	public static final String STATUS = "status";
	public static final String PATH = "path";
	public static final String ADDRESS = "address";
	public static final String METHOD = "method";
	public static final String PATTERN = "pattern";
	public static final String HEADERS = "headers";

	// --- CONTENT TYPES ---

	public static final String CONTENT_TYPE_JSON = "application/json;charset=utf-8";

}