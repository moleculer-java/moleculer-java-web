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

import static services.moleculer.util.CommonUtils.readFully;

import java.io.File;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import io.datatree.dom.Cache;
import services.moleculer.ServiceBroker;
import services.moleculer.error.MoleculerError;
import services.moleculer.service.Service;
import services.moleculer.service.ServiceRegistry;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.middleware.ServeStatic;

public final class GatewayUtils implements HttpConstants {

	// --- LOGGER ---

	private static final Logger logger = LoggerFactory.getLogger(GatewayUtils.class);

	// --- CACHES ---

	protected static final Cache<String, URL> urlCache = new Cache<>(2048);

	protected static final long jarTimestamp = System.currentTimeMillis();

	// --- ERROR HANDLER ---

	public static final void sendError(WebResponse rsp, Throwable cause) {
		try {
			MoleculerError error;
			if (cause instanceof MoleculerError) {
				error = (MoleculerError) cause;
			} else {
				Throwable err = cause;
				if (err != null && err.getCause() != null) {
					err = err.getCause();
				}
				String msg = null;
				String type = null;
				if (err != null) {
					msg = err.getMessage();
					type = err.getClass().getName();
					int i = type.lastIndexOf('.');
					if (i > -1) {
						type = type.substring(i + 1);
					}
					type = type.replaceAll("(.)(\\p{Upper})", "$1_$2").toUpperCase();
				}
				if (msg == null || msg.isEmpty()) {
					msg = "Unknown error occured!";
				}
				if (type == null || type.isEmpty()) {
					type = "MOLECULER_ERROR";
				}
				error = new MoleculerError(msg, cause, "unknown", false, 500, type, null);
			}
			Tree json = error.toTree();
			byte[] body = json.toBinary();
			rsp.setStatus(error.getCode());
			rsp.setHeader(CACHE_CONTROL, NO_CACHE);
			rsp.setHeader(CONTENT_TYPE, CONTENT_TYPE_JSON);
			rsp.setHeader(CONTENT_LENGTH, Integer.toString(body.length));
			rsp.send(body);
		} catch (Exception ignored) {
			logger.debug("Unable to send error response!", ignored);
		} finally {
			rsp.end();
		}
	}

	// --- FIND SERVICE BY CLASS ---

	@SuppressWarnings("unchecked")
	public static final <T extends Service> T getService(ServiceBroker broker, Class<T> type) {
		ServiceRegistry registry = broker.getConfig().getServiceRegistry();
		Tree info = registry.getDescriptor();
		for (Tree service : info.get("services")) {
			String name = service.get("name", "");
			if (name != null && !name.isEmpty()) {
				Service instance = broker.getLocalService(name);
				if (type.isAssignableFrom(instance.getClass())) {
					return (T) instance;
				}
			}
		}
		return null;
	}

	// --- COOKIE HANDLERS ---

	public static final String getCookieValue(WebRequest req, WebResponse rsp, String name) {
		HttpCookie cookie = getCookieMap(req, rsp).get(name);
		if (cookie == null) {
			return null;
		}
		return cookie.getValue();
	}

	public static final HttpCookie getCookie(WebRequest req, WebResponse rsp, String name) {
		return getCookieMap(req, rsp).get(name);
	}

	private static final HashMap<String, HttpCookie> getCookieMap(WebRequest req, WebResponse rsp) {

		// Get from properties
		@SuppressWarnings("unchecked")
		HashMap<String, HttpCookie> cookies = (HashMap<String, HttpCookie>) rsp.getProperty(PROPERTY_COOKIES);
		if (cookies != null) {
			return cookies;
		}

		// Parse cookies
		cookies = new HashMap<String, HttpCookie>();

		// Get from response
		String headerValue = req.getHeader(COOKIE);
		if (headerValue != null) {
			parseCookies(cookies, headerValue);
		}

		// Get from response
		headerValue = rsp.getHeader(SET_COOKIE);
		if (headerValue != null) {
			parseCookies(cookies, headerValue);
		}

		// Store cookie map
		rsp.setProperty(PROPERTY_COOKIES, cookies);
		return cookies;
	}

	private static final void parseCookies(HashMap<String, HttpCookie> cookies, String headerValue) {
		List<HttpCookie> list = HttpCookie.parse(headerValue);
		for (HttpCookie cookie : list) {
			cookies.put(cookie.getName(), cookie);
		}
	}

	public static final void setCookie(WebRequest req, WebResponse rsp, HttpCookie cookie) {
		HashMap<String, HttpCookie> cookies = getCookieMap(req, rsp);
		cookies.put(cookie.getName(), cookie);

		// Set new "Set-Cookie" header
		StringBuilder tmp = new StringBuilder(128);
		for (HttpCookie c : cookies.values()) {
			tmp.append(c.toString()).append(',');
		}
		int len = tmp.length();
		if (len > 0) {
			tmp.setLength(len - 1);
		}
		rsp.setHeader(SET_COOKIE, tmp.toString());
	}

	// --- FILE HANDLERS ---

	public static final boolean isReadable(String path) {
		try {
			return getFileURL(path) != null;
		} catch (Exception ignored) {
		}
		return false;
	}

	public static final long getFileSize(String path) {
		try {
			URL url = getFileURL(path);
			if (url != null) {
				if ("file".equals(url.getProtocol())) {
					File file = new File(new URI(url.toString()));
					return file.length();
				}
			}
		} catch (Exception ignored) {
			logger.debug("Unable to get file size: " + path);
		}
		return -1;
	}

	public static final long getLastModifiedTime(String path) {
		try {
			URL url = getFileURL(path);
			if (url != null) {
				if ("file".equals(url.getProtocol())) {
					File file = new File(new URI(url.toString()));
					return file.lastModified();
				}
			}
		} catch (Exception ignored) {
			logger.debug("Unable to get last modification time: " + path);
		}
		return jarTimestamp;
	}

	public static final byte[] readAllBytes(String path) {
		try {
			URL url = getFileURL(path);
			if (url != null) {
				return readFully(url.openStream());
			}
		} catch (Exception ignored) {
		}
		logger.debug("Unable to load file: " + path);
		return new byte[0];
	}

	public static final URL getFileURL(String path) {
		URL url = urlCache.get(path);
		if (url != null) {
			return url;
		}
		url = tryToGetFileURL(path);
		if (url != null) {
			urlCache.put(path, url);
			return url;
		}
		if (path.startsWith("/") && path.length() > 1) {
			url = tryToGetFileURL(path.substring(1));
			if (url != null) {
				urlCache.put(path, url);
			}
		}
		return url;
	}

	private static final URL tryToGetFileURL(String path) {
		try {
			File test = new File(path);
			if (test.isFile()) {
				return test.toURI().toURL();
			}
			URL url = ServeStatic.class.getResource(path);
			System.out.println("GET RESOURCE: " + url);
			if (url != null) {
				return url;
			}
			url = Thread.currentThread().getContextClassLoader().getResource(path);
			System.out.println("CLASSLOADER: " + url);
			if (url != null) {
				return url;
			}
		} catch (Exception cause) {
			logger.debug("Unable to open file: " + path, cause);
		}
		return null;
	}

}