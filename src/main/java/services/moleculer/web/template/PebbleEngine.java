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

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.pebbletemplates.pebble.error.LoaderException;
import io.pebbletemplates.pebble.loader.Loader;

import io.datatree.Tree;
import services.moleculer.web.common.GatewayUtils;

/**
 * Server-side template engine based on Pebble API. Required dependency:
 * 
 * <pre>
 * // https://mvnrepository.com/artifact/io.pebbletemplates/pebble
 * compile group: 'io.pebbletemplates', name: 'pebble', version: '3.2.4'
 * </pre>
 * 
 * @see VelocityEngine
 * @see DataTreeEngine
 * @see FreeMarkerEngine
 * @see HandlebarsEngine
 * @see MustacheEngine
 * @see ThymeleafEngine
 */
public class PebbleEngine extends AbstractTemplateEngine {

	// --- VARIABLES ---

	protected io.pebbletemplates.pebble.PebbleEngine engine;

	protected PebbleLoader loader = new PebbleLoader();

	// --- CONSTRUCTOR ---

	public PebbleEngine() {
		buildEngine();
	}

	protected void buildEngine() {
		engine = new io.pebbletemplates.pebble.PebbleEngine.Builder().loader(loader).cacheActive(!reloadable)
				.executorService(executor).build();
	}

	// --- TRANSFORM JSON TO HTML ---

	@SuppressWarnings("unchecked")
	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		StringWriter out = new StringWriter(writeBufferSize);
		engine.getTemplate(templatePath).evaluate(out, (Map<String, Object>) data.asObject());
		return out.toString().getBytes(loader.charset);
	}

	// --- ROOT PATH OF TEMPLATES ---

	@Override
	public void setTemplatePath(String templatePath) {
		super.setTemplatePath(templatePath);
		loader.templatePath = this.templatePath;
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	@Override
	public void setCharset(Charset charset) {
		super.setCharset(charset);
		loader.charset = this.charset;
	}

	// --- ENABLE / DISABLE RELOADING ---

	@Override
	public void setReloadable(boolean reloadable) {
		if (this.reloadable != reloadable) {
			super.setReloadable(reloadable);
			buildEngine();
		}
	}

	// --- DEFAULT EXTENSION ---

	@Override
	public void setDefaultExtension(String defaultExtension) {
		super.setDefaultExtension(defaultExtension);
		loader.extension = this.defaultExtension;
	}

	// --- OPTIONAL EXECUTOR (CAN BE NULL) ---

	@Override
	public void setExecutor(ExecutorService executor) {
		super.setExecutor(executor);
		buildEngine();
	}

	// --- GET/SET PEBBLE ENGINE ---

	public io.pebbletemplates.pebble.PebbleEngine getEngine() {
		return engine;
	}

	public void setEngine(io.pebbletemplates.pebble.PebbleEngine engine) {
		this.engine = Objects.requireNonNull(engine);
	}

	// --- LOADER CLASS ---

	public static class PebbleLoader implements Loader<String> {

		protected Charset charset = StandardCharsets.UTF_8;

		protected String templatePath = "";

		protected String extension = "html";

		@Override
		public Reader getReader(String cacheKey) throws LoaderException {
			return new StringReader(loadResource(templatePath, cacheKey, extension, charset));
		}

		@Override
		public boolean resourceExists(String templateName) {
			return GatewayUtils.isReadable(getAbsolutePath(templatePath, templateName, extension));
		}

		@Override
		public void setCharset(String charset) {
			this.charset = Charset.forName(charset);
		}

		@Override
		public void setPrefix(String prefix) {
			String path = Objects.requireNonNull(prefix);
			path = path.replace('\\', '/');
			while (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			this.templatePath = path;
		}

		@Override
		public void setSuffix(String suffix) {
			String ext = Objects.requireNonNull(suffix);
			while (ext.startsWith(".")) {
				ext = ext.substring(1);
			}
			this.extension = ext.trim();
		}

		@Override
		public String resolveRelativePath(String relativePath, String anchorPath) {
			return relativePath;
		}

		@Override
		public String createCacheKey(String templateName) {
			return templateName;
		}

	}

}