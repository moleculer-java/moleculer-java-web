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
package services.moleculer.web.template.languages;

import static services.moleculer.web.common.GatewayUtils.getLastModifiedTime;
import static services.moleculer.web.common.GatewayUtils.isReadable;
import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;

public class DefaultMessageLoader implements MessageLoader {

	// --- LOGGER ---

	private static final Logger logger = LoggerFactory.getLogger(DefaultMessageLoader.class);

	// --- VARIABLES ---

	protected String prefix = "languages/messages";
	protected String extension = "properties";
	protected boolean reloadable;

	protected ConcurrentHashMap<String, Tree> cache = new ConcurrentHashMap<>();
	
	// --- CONSTRUCTORS ---
	
	public DefaultMessageLoader() {
	}

	public DefaultMessageLoader(boolean reloadable) {
		this.reloadable = reloadable;
	}
	
	public DefaultMessageLoader(String prefix, String extension, boolean reloadable) {
		this.prefix = prefix;
		this.extension = extension;
		this.reloadable = reloadable;
	}

	// --- MESSAGE-FILE LOADER METHOD ---

	@Override
	public Tree loadMessages(String locale) {
		String key = locale.trim().toLowerCase();
		Tree messages;
		if (!reloadable) {
			messages = cache.get(key);
			if (messages != null) {
				return messages;
			}
		}
		
		// Default language
		if (locale.isEmpty()) {

			// Find "prefix.extension"...
			messages = tryToLoadMessages("");
			if (!reloadable && messages != null) {
				cache.put(key, messages);
			}
			return messages;
		}

		// Parse locale
		StringTokenizer st = new StringTokenizer(key, "_-");
		String l = st.nextToken();
		String c = st.hasMoreTokens() ? st.nextToken() : "";
		String v = st.hasMoreTokens() ? st.nextToken() : "";
		if (v.startsWith("#")) {
			v = "";
		}
		Tree mergedMessages = new Tree();

		// Find "prefix.extension"...
		mergeMessages(mergedMessages, tryToLoadMessages(""));
				
		// Find "prefix-language.extension"...
		mergeMessages(mergedMessages, tryToLoadMessages(l));

		// Find "prefix-language-country.extension"...
		if (!c.isEmpty()) {
			mergeMessages(mergedMessages, tryToLoadMessages(l + "-" + c));
		}

		// Find "prefix-language-country-variant.extension"...
		if (!c.isEmpty() && !v.isEmpty()) {
			mergeMessages(mergedMessages, tryToLoadMessages(l + "-" + c + "-" + v));
		}
		
		// Store in cache
		if (mergedMessages.isEmpty()) {
			return null;
		}
		if (!reloadable) {
			cache.put(key, mergedMessages);
		}
		return mergedMessages;
	}

	protected void mergeMessages(Tree mergedMessages, Tree messages) {
		if (messages == null) {
			return;
		}
		for (Tree item: messages) {
			if (item.isMap()) {
				mergeMessages(mergedMessages, item);
				continue;
			}
			mergedMessages.putObject(item.getPath(), item.asObject());
		}
	}
	
	protected Tree tryToLoadMessages(String locale) {
		try {

			// Calculate path
			String path;
			boolean defaultLanguage = locale.isEmpty();
			if (defaultLanguage) {
				path = prefix + '.' + extension;
			} else {
				path = prefix + '-' + locale + '.' + extension;
			}

			// Exists?
			if (!isReadable(path)) {
				return null;
			}

			// Load message file
			byte[] bytes = readAllBytes(path);
			String format = "yml".equals(extension) ? "yaml" : extension;
			Tree messages = new Tree(bytes, format);
			logger.info("Message file \"" + path + "\" loaded successfully.");
		
			// Return message file
			if (!reloadable) {
				cache.put(locale, messages);
			}
			return messages;

		} catch (Exception cause) {
			logger.error("Unable to load message file!", cause);
		}
		return null;
	}
	
	// --- CHECK CHANGES ---

	@Override
	public long getLastModified(String locale) {
		if (!reloadable) {
			
			// Time is not important in this case
			return 0;
		}
		long timestamp = getLastModifiedTime(prefix + '.' + extension);
		if (locale == null || locale.isEmpty()) {
			return timestamp;
		}
		
		// Parse locale
		String key = locale == null || locale.isEmpty() ? key = "" : locale.trim().toLowerCase();
		StringTokenizer st = new StringTokenizer(key, "_-");
		String l = st.nextToken();
		String c = st.hasMoreTokens() ? st.nextToken() : "";
		String v = st.hasMoreTokens() ? st.nextToken() : "";
		if (v.startsWith("#")) {
			v = "";
		}
		
		// Find "prefix-language-country-variant.extension"...
		if (!c.isEmpty() && !v.isEmpty()) {
			timestamp = Math.max(timestamp, tryToCheckTimestamp(l + "-" + c + "-" + v));
		}

		// Find "prefix-language-country.extension"...
		if (!c.isEmpty()) {
			timestamp = Math.max(timestamp, tryToCheckTimestamp(l + "-" + c));
		}

		// Find "prefix-language.extension"...
		return Math.max(timestamp, tryToCheckTimestamp(l));
	}

	protected long tryToCheckTimestamp(String locale) {
		try {
			
			// Calculate path
			String path;
			boolean defaultLanguage = locale.isEmpty();
			if (defaultLanguage) {
				path = prefix + '.' + extension;
			} else {
				path = prefix + '-' + locale + '.' + extension;
			}
			
			// Get timestamp (or -1)
			return getLastModifiedTime(path);
			
		} catch (Exception cause) {
			logger.error("Unable to check timestamp!", cause);
		}
		return -1;
	}
	
	// --- GETTERS / SETTERS ---

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public String getExtension() {
		return extension;
	}

	public void setExtension(String extension) {
		this.extension = extension;
	}

	public boolean isReloadable() {
		return reloadable;
	}

	public void setReloadable(boolean reloadable) {
		this.reloadable = reloadable;
	}
	
}