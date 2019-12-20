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
package services.moleculer.web.template;

import static services.moleculer.web.common.GatewayUtils.getLastModifiedTime;
import static services.moleculer.web.common.GatewayUtils.isReadable;
import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache;
import com.github.jknack.handlebars.io.AbstractTemplateLoader;
import com.github.jknack.handlebars.io.TemplateSource;

import io.datatree.Tree;

/**
 * Server-side template engine based on Handlebars API. Required dependency:
 * 
 * <pre>
 * // https://mvnrepository.com/artifact/com.github.jknack/handlebars
 * compile group: 'com.github.jknack', name: 'handlebars', version: '4.1.2'
 * </pre>
 * 
 * @see VelocityEngine
 * @see DataTreeEngine
 * @see FreeMarkerEngine
 * @see JadeEngine
 * @see MustacheEngine
 * @see PebbleEngine
 * @see ThymeleafEngine
 */
public class HandlebarsEngine extends AbstractTemplateEngine {

	// --- VARIABLES ---

	protected Handlebars engine;

	protected HandlebarsLoader loader = new HandlebarsLoader();

	protected ConcurrentMapTemplateCache cache = new ConcurrentMapTemplateCache();

	protected ConcurrentHashMap<String, Template> templates = new ConcurrentHashMap<>();

	// --- CONSTRUCTOR ---

	public HandlebarsEngine() {
		engine = new Handlebars(loader).with(cache);
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		StringWriter out = new StringWriter(writeBufferSize);
		if (reloadable) {

			// Reloading enabled
			engine.compile(templatePath).apply(data.asObject(), out);

		} else {

			// Use cache
			Template template = templates.get(templatePath);
			if (template == null) {
				template = engine.compile(templatePath);
				templates.put(templatePath, template);
			}
			template.apply(data.asObject(), out);

		}
		return out.toString().getBytes(charset);
	}

	// --- ROOT PATH OF TEMPLATES ---

	@Override
	public void setTemplatePath(String templatePath) {
		super.setTemplatePath(templatePath);
		loader.setPrefix(this.templatePath);
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	@Override
	public void setCharset(Charset charset) {
		super.setCharset(charset);
		loader.setCharset(this.charset);
	}

	// --- DEFAULT EXTENSION ---

	@Override
	public void setDefaultExtension(String defaultExtension) {
		super.setDefaultExtension(defaultExtension);
		loader.setSuffix(this.defaultExtension);
	}

	// --- GET/SET HANDLEBARS ENGINE ---

	public Handlebars getEngine() {
		return engine;
	}

	public void setEngine(Handlebars engine) {
		this.engine = Objects.requireNonNull(engine);
	}

	// --- ENABLE / DISABLE RELOADING ---

	@Override
	public void setReloadable(boolean reloadable) {
		if (this.reloadable != reloadable) {
			templates.clear();
			cache.clear();
		}
		super.setReloadable(reloadable);
		cache.setReload(this.reloadable);
	}

	// --- LOADER CLASS ---

	public static class HandlebarsLoader extends AbstractTemplateLoader {

		@Override
		public TemplateSource sourceAt(String location) throws IOException {

			String resourcePath = getAbsolutePath(getPrefix(), location, getSuffix());
			if (!isReadable(resourcePath)) {
				return null;
			}
			long lastModified = getLastModifiedTime(resourcePath);
			byte[] bytes = readAllBytes(resourcePath);			
			
			return new TemplateSource() {

				@Override
				public long lastModified() {
					return lastModified;
				}

				@Override
				public String filename() {
					return location;
				}

				@Override
				public String content(Charset charset) throws IOException {
					return new String(bytes, charset);
				}

				@Override
				public int hashCode() {
					return location.hashCode();
				}

				@Override
				public boolean equals(Object obj) {
					return location.equals(((TemplateSource) obj).filename());
				}

			};
		}

	}

}