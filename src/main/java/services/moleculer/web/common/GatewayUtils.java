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
import java.net.URI;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import io.datatree.dom.Cache;
import services.moleculer.ServiceBroker;
import services.moleculer.service.Service;
import services.moleculer.service.ServiceRegistry;
import services.moleculer.web.middleware.ServeStatic;

public final class GatewayUtils {

	// --- LOGGER ---

	private static final Logger logger = LoggerFactory.getLogger(GatewayUtils.class);

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

	// --- FILE HANDLERS ---

	protected static final Cache<String, URL> urlCache = new Cache<>(2048);

	protected static final long jarTimestamp = System.currentTimeMillis();

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
		logger.warn("Unable to load file: " + path);
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
			if (url != null) {
				return url;
			}
			url = Thread.currentThread().getContextClassLoader().getResource(path);
			if (url != null) {
				return url;
			}
		} catch (Exception cause) {
			logger.warn("Unable to open file: " + path, cause);
		}
		return null;
	}

}