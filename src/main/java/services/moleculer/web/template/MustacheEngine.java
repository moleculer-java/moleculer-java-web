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
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheResolver;

import io.datatree.Tree;

/**
 * Server-side template engine based on Mustache API. Required dependency:
 * 
 * <pre>
 * // https://mvnrepository.com/artifact/com.github.spullara.mustache.java/compiler
 * compile group: 'com.github.spullara.mustache.java', name: 'compiler', version: '0.9.6'
 * </pre>
 * 
 * @see DataTreeEngine
 * @see FreeMarkerEngine
 * @see HandlebarsEngine
 * @see JadeEngine
 * @see PebbleEngine
 * @see ThymeleafEngine
 */
public class MustacheEngine extends AbstractTemplateEngine {

	// --- VARIABLES ---

	protected DefaultMustacheFactory factory;

	protected MustacheLoader loader = new MustacheLoader();

	// --- CONSTRUCTOR ---

	public MustacheEngine() {
		factory = new DefaultMustacheFactory(loader);
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		StringWriter out = new StringWriter(writeBufferSize);
		if (reloadable) {

			// Always load templates
			Reader reader = loader.getReader(templatePath);
			factory.compile(reader, templatePath).execute(out, data.asObject());

		} else {

			// Use cache
			factory.compile(templatePath).execute(out, data.asObject());

		}
		return out.toString().getBytes(charset);
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
		factory.setExecutorService(this.executor);
	}

	// --- MUSTACHE FACTORY ---

	public DefaultMustacheFactory getFactory() {
		return factory;
	}

	public void setFactory(DefaultMustacheFactory factory) {
		this.factory = Objects.requireNonNull(factory);
	}

	// --- LOADER CLASS ---

	public static class MustacheLoader implements MustacheResolver {

		// --- VARIABLES ---

		protected Charset charset = StandardCharsets.UTF_8;

		protected String templatePath = "";

		protected String extension = "html";

		// --- LOADER METHOD ---

		@Override
		public Reader getReader(String name) {
			return new StringReader(loadResource(templatePath, name, extension, charset));
		}

	}

}