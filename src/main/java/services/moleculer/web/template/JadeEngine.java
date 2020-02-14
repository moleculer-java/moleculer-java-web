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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import de.neuland.jade4j.Jade4J.Mode;
import de.neuland.jade4j.JadeConfiguration;
import de.neuland.jade4j.template.TemplateLoader;
import io.datatree.Tree;

/**
 * Server-side template engine based on Jade4J API. Required dependency:
 * 
 * <pre>
 * // https://mvnrepository.com/artifact/de.neuland-bfi/jade4j
 * compile group: 'de.neuland-bfi', name: 'jade4j', version: '1.3.0'
 * </pre>
 * 
 * @see VelocityEngine
 * @see DataTreeEngine
 * @see FreeMarkerEngine
 * @see HandlebarsEngine
 * @see MustacheEngine
 * @see PebbleEngine
 * @see ThymeleafEngine
 */
public class JadeEngine extends AbstractTemplateEngine {

	// --- VARIABLES ---

	protected JadeConfiguration configuration = new JadeConfiguration();

	protected JadeLoader loader = new JadeLoader();

	// --- CONSTRUCTOR ---

	public JadeEngine() {
		configuration.setTemplateLoader(loader);
		configuration.setCaching(true);
		configuration.setMode(Mode.HTML);
		configuration.setPrettyPrint(false);
	}

	// --- TRANSFORM JSON TO HTML ---

	@SuppressWarnings("unchecked")
	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		StringWriter out = new StringWriter(writeBufferSize);
		configuration.renderTemplate(configuration.getTemplate(templatePath), (Map<String, Object>) data.asObject(),
				out);
		return out.toString().getBytes(loader.charset);
	}

	// --- ENABLE / DISABLE RELOADING ---

	@Override
	public void setReloadable(boolean reloadable) {
		super.setReloadable(reloadable);
		configuration.setCaching(!this.reloadable);
		loader.reloadable = this.reloadable;
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

	// --- GET/SET JADE CONFIGURATION ---

	public JadeConfiguration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(JadeConfiguration configuration) {
		this.configuration = Objects.requireNonNull(configuration);
	}

	// --- TEMPLATE LOADER CLASS ---

	public static class JadeLoader implements TemplateLoader {

		// --- VARIABLES ---

		protected Charset charset = StandardCharsets.UTF_8;

		protected String templatePath = "";

		protected String extension = "html";

		protected boolean reloadable;

		// --- LOADER METHODS ---

		@Override
		public long getLastModified(String name) throws IOException {
			return getLastModifiedMillis(templatePath, name, extension, reloadable);
		}

		@Override
		public Reader getReader(String name) throws IOException {
			return new StringReader(loadResource(templatePath, name, extension, charset));
		}

		@Override
		public String getExtension() {
			return extension;
		}

	}

}