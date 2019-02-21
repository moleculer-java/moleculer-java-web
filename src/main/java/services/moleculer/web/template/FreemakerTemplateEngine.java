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
package services.moleculer.web.template;

import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import freemarker.cache.NullCacheStorage;
import freemarker.cache.StrongCacheStorage;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.Version;
import io.datatree.Tree;
import services.moleculer.web.template.freemaker.FreemakerLoader;
import services.moleculer.web.template.freemaker.FreemakerTreeWrapper;

public class FreemakerTemplateEngine extends Configuration implements TemplateEngine {

	// --- VARIABLES ---

	protected boolean reloadable;

	protected int writeBufferSize = 2048;

	protected ConcurrentHashMap<String, Template> eternalCache = new ConcurrentHashMap<>(512, 0.75f,
			128);

	protected FreemakerLoader loader = new FreemakerLoader();

	protected Charset charset = StandardCharsets.UTF_8;

	// --- CONSTRUCTOR ---

	public FreemakerTemplateEngine() {
		this(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
	}

	public FreemakerTemplateEngine(Version incompatibleImprovements) {
		super(incompatibleImprovements);

		// Set Tree property wrapper
		setObjectWrapper(new FreemakerTreeWrapper());

		// Set template loader
		setTemplateLoader(loader);

		// Disable internal cache by default
		setReloadable(false);

		// Disable localized lookups by default
		setLocalizedLookup(false);

		// Set the default encoding to UTF-8
		setEncoding(getLocale(), "UTF-8");
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		Template template = null;
		if (reloadable) {
			template = getTemplate(templatePath);
		} else {
			template = eternalCache.get(templatePath);
			if (template == null) {
				template = getTemplate(templatePath);
				if (template != null) {
					eternalCache.put(templatePath, template);
				}
			}			
		}
		StringWriter out = new StringWriter(writeBufferSize);
		template.process(data, out);
		return out.toString().getBytes(charset);
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	public Charset getCharset() {
		charset = Charset.forName(getEncoding(getLocale()));
		return charset;
	}

	@Override
	public void setCharset(Charset charset) {
		if (!this.charset.equals(charset)) {
			setEncoding(getLocale(), charset.name());
			this.charset = charset;
		}
	}

	// --- ENABLE / DISABLE RELOADING ---

	public boolean isReloadable() {
		return reloadable;
	}

	public void setReloadable(boolean reloadable) {
		if (this.reloadable != reloadable) {
			this.reloadable = reloadable;
			if (reloadable) {
				eternalCache.clear();
				setCacheStorage(new NullCacheStorage());
			} else {
				setCacheStorage(new StrongCacheStorage());
			}
		}
	}

	// --- ROOT PATH OF TEMPLATES ---

	public String getTemplatePath() {
		return loader.getTemplatePath();
	}

	@Override
	public void setTemplatePath(String templatePath) {
		loader.setTemplatePath(templatePath);
	}

	// --- INITIAL SIZE OF WRITE BUFFER ---

	public int getWriteBufferSize() {
		return writeBufferSize;
	}

	public void setWriteBufferSize(int writeBufferSize) {
		this.writeBufferSize = writeBufferSize;
	}

}