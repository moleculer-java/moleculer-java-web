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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.resource.Resource;
import org.apache.velocity.runtime.resource.loader.ResourceLoader;
import org.apache.velocity.util.ExtProperties;

import io.datatree.Tree;

/**
 * Server-side template engine based on Apache Velocity API. Required
 * dependency:
 * 
 * <pre>
 * // https://mvnrepository.com/artifact/org.apache.velocity/velocity-engine-core
 * compile group: 'org.apache.velocity', name: 'velocity-engine-core', version: '2.1'
 * </pre>
 * 
 * @see DataTreeEngine
 * @see FreeMarkerEngine
 * @see HandlebarsEngine
 * @see JadeEngine
 * @see MustacheEngine
 * @see PebbleEngine
 * @see ThymeleafEngine
 */
public class VelocityEngine extends AbstractTemplateEngine {

	// --- VARIABLES ---

	/**
	 * Internal Velocity instance.
	 */
	protected org.apache.velocity.app.VelocityEngine engine = new org.apache.velocity.app.VelocityEngine();

	/**
	 * A list of template files containing macros to be used when merging.
	 */
	protected List<?> macroLibraries;

	/**
	 * Charset name.
	 */
	protected String charsetName = "UTF-8";

	/**
	 * Is VelocityEngine inited?
	 */
	protected AtomicBoolean inited = new AtomicBoolean();

	/**
	 * Resource loader.
	 */
	protected VelocityResourceLoader loader = new VelocityResourceLoader();

	// --- CONSTRUCTOR ---

	public VelocityEngine() {
		engine.setProperty(Velocity.INPUT_ENCODING, "UTF-8");
		engine.setProperty(Velocity.RESOURCE_LOADERS, "moleculer");
		engine.setProperty("resource.loader.moleculer.instance", loader);
		engine.setProperty("resource.loader.moleculer.cache", true);
		engine.setProperty("resource.loader.moleculer.modification_check_interval", 0);
	}

	// --- TRANSFORM JSON TO HTML ---

	@SuppressWarnings("unchecked")
	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		if (inited.compareAndSet(false, true)) {
			engine.init();
		}
		StringWriter out = new StringWriter(writeBufferSize);
		VelocityContext ctx = new VelocityContext((Map<String, Object>) data.asObject());
		engine.getTemplate(templatePath, charsetName).merge(ctx, out, macroLibraries);
		return out.toString().getBytes(charset);
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	@Override
	public void setCharset(Charset charset) {
		super.setCharset(charset);
		charsetName = charset.name();
		engine.setProperty(Velocity.INPUT_ENCODING, charsetName);
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
		engine.setProperty("resource.loader.moleculer.modification_check_interval", reloadable ? 1 : 0);
		loader.reloadable = this.reloadable;
	}

	// --- GET/SET VELOCITY TEMPLATE ENGINE ---

	public org.apache.velocity.app.VelocityEngine getEngine() {
		return engine;
	}

	public void setEngine(org.apache.velocity.app.VelocityEngine engine) {
		this.engine = Objects.requireNonNull(engine);
	}

	// --- SET ENGINE PROPERTY ---

	public void setProperty(String key, Object value) {
		engine.setProperty(key, value);
	}

	// --- SET ENGINE PROPERTIES ---

	public void setProperties(Properties configuration) {
		engine.setProperties(configuration);
	}

	public void setProperties(String propsFilename) {
		engine.setProperties(propsFilename);
	}

	// --- SET APPLICATION ATTRIBUTE ---

	public void setApplicationAttribute(String key, Object value) {
		engine.setApplicationAttribute(key, value);
	}

	// --- GET/SET MACRO LIBRARIES ---

	public List<?> getMacroLibraries() {
		return macroLibraries;
	}

	public void setMacroLibraries(List<?> macroLibraries) {
		this.macroLibraries = macroLibraries;
	}

	// --- LOADER CLASS ---

	public static class VelocityResourceLoader extends ResourceLoader {

		// --- VARIABLES ---

		protected String templatePath = "";

		protected String extension = "html";

		protected boolean reloadable;

		protected Charset charset = StandardCharsets.UTF_8;

		// --- LOADER METHOD ---

		@Override
		public Reader getResourceReader(String source, String encoding) throws ResourceNotFoundException {
			return new StringReader(loadResource(templatePath, source, extension,
					encoding == null ? charset : Charset.forName(encoding)));
		}

		@Override
		public void init(ExtProperties configuration) {

			// Do nothing
		}

		@Override
		public boolean isSourceModified(Resource resource) {
			return resource.getLastModified() != getLastModified(resource);
		}

		@Override
		public long getLastModified(Resource resource) {
			return getLastModifiedMillis(templatePath, resource.getName(), extension, reloadable);
		}

	}

}