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

import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheResolver;

import io.datatree.Tree;

public class MustacheEngine implements TemplateEngine {

	// --- VARIABLES ---

	protected boolean reloadable;

	protected int writeBufferSize = 2048;

	protected DefaultMustacheFactory sharedFactory;

	protected MustacheLoader loader = new MustacheLoader();

	protected ConcurrentHashMap<String, Mustache> eternalCache = new ConcurrentHashMap<>(512, 0.75f, 128);

	protected Charset charset = StandardCharsets.UTF_8;

	// --- CONSTRUCTOR ---

	public MustacheEngine() {
		sharedFactory = new DefaultMustacheFactory(loader);
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		Mustache template = null;
		if (reloadable) {

			// Slow method (it disables internal cache of
			// DefaultMustacheFactory)
			DefaultMustacheFactory pageFactory = new DefaultMustacheFactory(loader);
			template = pageFactory.compile(templatePath);

		} else {

			// Fast method (all templates are cached)
			template = eternalCache.get(templatePath);
			if (template == null) {
				template = sharedFactory.compile(templatePath);
				if (template != null) {
					eternalCache.put(templatePath, template);
				}
			}

		}
		
		// Render template
		StringWriter out = new StringWriter(writeBufferSize);
		template.execute(out, data.asObject());
		return out.toString().getBytes(charset);
	}

	// --- ROOT PATH OF TEMPLATES ---

	@Override
	public void setTemplatePath(String templatePath) {
		loader.setTemplatePath(templatePath);
	}

	public String getTemplatePath() {
		return loader.getTemplatePath();
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	public Charset getCharset() {
		return charset;
	}

	@Override
	public void setCharset(Charset charset) {
		this.charset = charset;
		loader.setCharset(charset);
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
			}
		}
	}

	public int getWriteBufferSize() {
		return writeBufferSize;
	}

	public void setWriteBufferSize(int writeBufferSize) {
		this.writeBufferSize = writeBufferSize;
	}

	// --- LOADER CLASS ---
	
	protected static class MustacheLoader implements MustacheResolver {

		// --- VARIABLES ---

		protected Charset charset = StandardCharsets.UTF_8;
		
		protected String templatePath = "";
		
		// --- LOADER METHOD ---
		
		@Override
		public Reader getReader(String name) {
			String path = templatePath + '/' + name.replace('\\', '/');
			byte[] bytes = readAllBytes(path);
			String template = new String(bytes, charset);
			return new StringReader(template);
		}

		// --- GETTERS / SETTERS ---

		protected Charset getCharset() {
			return charset;
		}

		protected void setCharset(Charset charset) {
			this.charset = charset;
		}
		
		protected void setTemplatePath(String templatePath) {
			templatePath = templatePath.replace('\\', '/');
			while (templatePath.endsWith("/")) {
				templatePath = templatePath.substring(0, templatePath.length() - 1);
			}
			this.templatePath = templatePath;
		}

		protected String getTemplatePath() {
			return templatePath;
		}
		
	}
	
}