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

import static services.moleculer.web.common.GatewayUtils.getLastModifiedTime;
import static services.moleculer.web.common.GatewayUtils.isReadable;
import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import de.neuland.jade4j.JadeConfiguration;
import de.neuland.jade4j.template.JadeTemplate;
import de.neuland.jade4j.template.TemplateLoader;
import io.datatree.Tree;

public class JadeEngine extends JadeConfiguration implements TemplateEngine {

	// --- VARIABLES ---
	
	protected Charset charset = StandardCharsets.UTF_8;
	
	protected int writeBufferSize = 2048;
	
	protected JadeLoader loader = new JadeLoader();
	
	// --- CONSTRUCTOR ---
	
	public JadeEngine() {
		super();

		// Set template loader
		setTemplateLoader(loader);
	}
	
	// --- TRANSFORM JSON TO HTML ---
	
	@SuppressWarnings("unchecked")
	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		JadeTemplate template = getTemplate(templatePath);

		// Render template
		StringWriter out = new StringWriter(writeBufferSize);
		Map<String, Object> map = null;
		if (data.isMap()) {
			map = (Map<String, Object>) data.asObject();
		} else {
			map = new HashMap<String, Object>();
			map.put("data", data.asObject());
		}
		renderTemplate(template, map, out);
		return out.toString().getBytes(charset);
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
		return !isCaching();
	}

	@Override
	public void setReloadable(boolean reloadable) {
		setCaching(!reloadable);
	}

	// --- ROOT PATH OF TEMPLATES ---

	public String getTemplatePath() {
		return getBasePath();
	}

	@Override
	public void setTemplatePath(String templatePath) {
		setBasePath(templatePath);
	}

	// --- INITIAL SIZE OF WRITE BUFFER ---
	
	public int getWriteBufferSize() {
		return writeBufferSize;
	}

	public void setWriteBufferSize(int writeBufferSize) {
		this.writeBufferSize = writeBufferSize;
	}
	
	// --- LOADER CLASS ---
	
	protected static class JadeLoader implements TemplateLoader {

		// --- VARIABLES ---

		protected Charset charset = StandardCharsets.UTF_8;
		
		protected String extension = "jade";
		
		// --- LOADER METHODS ---
		
		@Override
		public long getLastModified(String name) throws IOException {
			return getLastModifiedTime(name);
		}

		@Override
		public Reader getReader(String name) throws IOException {
			if (!isReadable(name)) {
				throw new IOException("File not found:" + name);
			}
			byte[] bytes = readAllBytes(name);
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

		@Override
		public String getExtension() {
			return extension;
		}

		protected void setExtension(String extension) {
			this.extension = extension;
		}
		
	}
	
}