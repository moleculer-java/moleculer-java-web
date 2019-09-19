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

import static services.moleculer.util.CommonUtils.formatPath;
import static services.moleculer.web.common.GatewayUtils.sendError;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import io.datatree.dom.Cache;
import services.moleculer.cacher.Cacher;
import services.moleculer.eventbus.Matcher;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * URL-based content cache. It is good for caching the responses of
 * non-authenticated REST services with large responses. For example, if the
 * service generates blog/wiki content using the template engine. It is not
 * advisable to cache POST requests and/or requests that depend not only on the
 * URL but also on the content of the request. TopLevelCache speeds up querying
 * of various reports (tables, charts) and dynamically generated images.
 */
@Name("Top-level Cache")
public class TopLevelCache extends HttpMiddleware implements HttpConstants {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(TopLevelCache.class);

	// --- PROPERTIES ---

	protected final Cache<String, Boolean> pathCache = new Cache<>(512);

	protected final Cacher cacher;

	/**
	 * Cache region (~= prefix).
	 */
	protected String region = "toplevel";

	/**
	 * Expire time, in SECONDS (0 = never expires)
	 */
	protected int ttl = 0;

	protected String[] pathPatterns = new String[0];

	/**
	 * Use ETag headers
	 */
	protected boolean useETags = true;
	
	// --- CONSTRUCTORS ---

	public TopLevelCache(Cacher cacher, String... pathPatterns) {
		this.cacher = Objects.requireNonNull(cacher);
		addPathPattern(pathPatterns);
	}

	// --- CREATE NEW PROCESSOR ---

	@Override
	public RequestProcessor install(RequestProcessor next, Tree config) {
		return new RequestProcessor() {

			/**
			 * Handles request of the HTTP client.
			 * 
			 * @param req
			 *            WebRequest object that contains the request the client
			 *            made of the ApiGateway
			 * @param rsp
			 *            WebResponse object that contains the response the
			 *            ApiGateway returns to the client
			 * 
			 * @throws Exception
			 *             if an input or output error occurs while the
			 *             ApiGateway is handling the HTTP request
			 */
			@Override
			public void service(WebRequest req, WebResponse rsp) throws Exception {

				// Check path
				String path = req.getPath();
				Boolean found = null;
				if (pathPatterns != null && pathPatterns.length > 0) {
					found = pathCache.get(path);
					if (found == null) {
						for (String pathPattern : pathPatterns) {
							if (Matcher.matches(path, pathPattern)) {
								found = true;
								break;
							}
						}
						if (found == null) {
							found = false;
						}
						pathCache.put(path, found);
					}
				}
				if (!found) {
					next.service(req, rsp);
					return;
				}

				// Try to load from cache
				String key = region + '.' + path + '|' + req.getHeader(ACCEPT_ENCODING);
				cacher.get(key).then(in -> {
					if (in == null || in.isNull()) {

						// Not in the cache
						ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
						Tree data = new Tree();
						Tree headers = data.putMap("headers");
						
						// Invoke next handler / action
						next.service(req, new WebResponse() {

							AtomicBoolean finished = new AtomicBoolean();

							@Override
							public final void setStatus(int code) {
								rsp.setStatus(code);
								data.put("status", code);
							}

							@Override
							public final int getStatus() {
								return rsp.getStatus();
							}

							@Override
							public final void setHeader(String name, String value) {
								rsp.setHeader(name, value);
								headers.put(name, value);
							}

							@Override
							public final String getHeader(String name) {
								return rsp.getHeader(name);
							}

							@Override
							public final void send(byte[] bytes) throws IOException {
								buffer.write(bytes);
							}

							@Override
							public final boolean end() {
								if (finished.compareAndSet(false, true)) {
									boolean ok;
									try {
										
										// Add ETag header
										if (useETags) {
											setHeader(ETAG, Long.toHexString(Math.abs(System.nanoTime())));
											setHeader(CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0");
										}

										// Store in cache
										byte[] body = buffer.toByteArray();
										rsp.send(body);
										if (body.length > 0) {
											data.put("body", body);
										}
										cacher.set(key, data, ttl);

									} catch (Exception cause) {
										logger.error("Unable to store content!", cause);
									} finally {
										ok = rsp.end();
									}
									return ok;
								}
								return false;
							}

							@Override
							public final void setProperty(String name, Object value) {
								rsp.setProperty(name, value);
							}

							@Override
							public final Object getProperty(String name) {
								return rsp.getProperty(name);
							}

						});

					} else {

						// Found in cache
						try {
							Tree headers = in.get("headers");
							if (headers != null) {
								if (useETags) {
									String etag = headers.get(ETAG, (String) null);
									if (etag != null) {
										String ifNoneMatch = req.getHeader(IF_NONE_MATCH);
										if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
											rsp.setStatus(304);
											rsp.setHeader(CONTENT_LENGTH, "0");
											return;
										}
									}
								}
								rsp.setStatus(in.get("status", 200));
								for (Tree header : headers) {
									rsp.setHeader(header.getName(), header.asString());
								}
							}
							Tree body = in.get("body");
							if (body != null) {
								rsp.send(body.asBytes());
							}
						} finally {
							rsp.end();
						}

					}
				}).catchError(err -> {
					sendError(rsp, err);
				});
			}
		};
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public int getTtl() {
		return ttl;
	}

	public void setTtl(int ttl) {
		this.ttl = ttl;
	}

	public String[] getPathPatterns() {
		return pathPatterns;
	}

	public void setPathPatterns(String... pathPatterns) {
		addPathPattern(pathPatterns);
	}

	public TopLevelCache addPathPattern(String... pathPatterns) {
		if (pathPatterns == null || pathPatterns.length == 0) {
			return this;
		}
		LinkedList<String> list = new LinkedList<>();
		if (this.pathPatterns != null) {
			list.addAll(Arrays.asList(this.pathPatterns));
		}
		for (String pathPattern : pathPatterns) {
			if (pathPattern == null || pathPattern.isEmpty()) {
				continue;
			}
			if ("*".equals(pathPattern) || "/*".equals(pathPattern) || "/**".equals(pathPattern)) {
				list.clear();
				list.addLast("/**");
				break;
			}
			list.add(formatPath(pathPattern));
		}
		this.pathPatterns = new String[list.size()];
		list.toArray(this.pathPatterns);

		// Return this (for method chaining)
		return this;
	}

}