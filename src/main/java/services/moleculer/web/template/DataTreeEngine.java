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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.datatree.Tree;
import io.datatree.templates.ResourceLoader;
import io.datatree.templates.TemplateEngine;

/**
 * Server-side template engine based on DataTreeTemplates API. Required
 * dependency:
 * 
 * <pre>
 * // https://mvnrepository.com/artifact/com.github.berkesa/datatree-templates
 * compile group: 'com.github.berkesa', name: 'datatree-templates', version: '1.1.3'
 * </pre>
 * 
 * @see VelocityEngine
 * @see FreeMarkerEngine
 * @see HandlebarsEngine
 * @see JadeEngine
 * @see MustacheEngine
 * @see PebbleEngine
 * @see ThymeleafEngine
 */
public class DataTreeEngine extends AbstractTemplateEngine {

	// --- VARIABLES ---

	protected DataTreeLoader loader = new DataTreeLoader();

	protected TemplateEngine engine = new TemplateEngine();

	// --- CONSTRUCTOR ---

	public DataTreeEngine() {
		engine.setLoader(loader);
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		return engine.process(templatePath, data).getBytes(charset);
	}

	// --- WRITE BUFFER SIZE ---

	@Override
	public void setWriteBufferSize(int writeBufferSize) {
		super.setWriteBufferSize(writeBufferSize);
		engine.setWriteBufferSize(this.writeBufferSize);
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	@Override
	public void setCharset(Charset charset) {
		this.charset = charset;
		engine.setCharset(this.charset);
		loader.charset = this.charset;
	}

	// --- ROOT PATH OF TEMPLATES ---

	@Override
	public void setTemplatePath(String templatePath) {
		super.setTemplatePath(templatePath);
		loader.templatePath = this.templatePath;
	}

	// --- DEFAULT EXTENSION ---

	@Override
	public void setDefaultExtension(String defaultExtension) {
		super.setDefaultExtension(defaultExtension);
		loader.extension = this.defaultExtension;
	}

	// --- ENABLE / DISABLE RELOADING ---

	@Override
	public void setReloadable(boolean reloadable) {
		super.setReloadable(reloadable);
		loader.reloadable = reloadable;
		engine.setReloadTemplates(reloadable);
	}

	// --- GET/SET DATATREE TEMPLATE ENGINE ---

	public TemplateEngine getEngine() {
		return engine;
	}

	public void setEngine(TemplateEngine engine) {
		this.engine = Objects.requireNonNull(engine);
	}

	// --- TEMPLATE LOADER ---

	public static class DataTreeLoader implements ResourceLoader {

		protected Charset charset = StandardCharsets.UTF_8;

		protected String templatePath = "";

		protected String extension = "html";

		protected boolean reloadable;

		@Override
		public String loadTemplate(String name, Charset charset) throws IOException {
			return loadResource(templatePath, name, extension, charset);
		}

		@Override
		public long lastModified(String name) {
			return getLastModifiedMillis(templatePath, name, extension, reloadable);
		}

	}

}