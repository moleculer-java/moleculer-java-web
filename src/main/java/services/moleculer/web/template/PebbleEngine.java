package services.moleculer.web.template;

import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.mitchellbosecke.pebble.error.LoaderException;
import com.mitchellbosecke.pebble.loader.Loader;
import com.mitchellbosecke.pebble.template.PebbleTemplate;

import io.datatree.Tree;

public class PebbleEngine implements TemplateEngine {

	// --- VARIABLES ---
	
	protected boolean reloadable;

	protected int writeBufferSize = 2048;

	protected com.mitchellbosecke.pebble.PebbleEngine engine;
	
	protected PebbleLoader loader = new PebbleLoader();
	
	// --- CONSTRUCTOR ---

	public PebbleEngine() {
		buildEngine();
	}
	
	protected void buildEngine() {
		engine = new com.mitchellbosecke.pebble.PebbleEngine.Builder().loader(loader).cacheActive(!reloadable).build();		
	}
	
	// --- TRANSFORM JSON TO HTML ---

	@SuppressWarnings("unchecked")
	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		PebbleTemplate template = engine.getTemplate(templatePath);

		// Render template
		StringWriter out = new StringWriter(writeBufferSize);
		Map<String, Object> map = null;
		if (data.isMap()) {
			map = (Map<String, Object>) data.asObject();
		} else {
			map = new HashMap<String, Object>();
			map.put("data", data.asObject());
		}
		template.evaluate(out, map);
		return out.toString().getBytes(loader.charset);
	}

	// --- ROOT PATH OF TEMPLATES ---

	@Override
	public void setTemplatePath(String templatePath) {
		loader.templatePath = templatePath;
	}

	public String getTemplatePath() {
		return loader.templatePath;
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---
	
	public Charset getCharset() {
		return loader.charset;
	}

	@Override
	public void setCharset(Charset charset) {
		loader.charset = charset;
	}

	// --- INITIAL SIZE OF WRITE BUFFER ---
	
	public int getWriteBufferSize() {
		return writeBufferSize;
	}

	public void setWriteBufferSize(int writeBufferSize) {
		this.writeBufferSize = writeBufferSize;
	}
	
	// --- ENABLE / DISABLE RELOADING ---

	public boolean isReloadable() {
		return reloadable;
	}

	@Override
	public void setReloadable(boolean reloadable) {
		if (this.reloadable != reloadable) {
			this.reloadable = reloadable;
			buildEngine();
		}
	}
	
	// --- EXTENSION OF TEMPLATES ---

	public String getExtension() {
		return loader.extension;
	}

	public void setExtension(String extension) {
		loader.extension = extension;
	}
	
	// --- LOADER CLASS ---
	
	protected static class PebbleLoader implements Loader<String> {
    
		protected Charset charset = StandardCharsets.UTF_8;
		
		protected String templatePath = "";

		protected String extension = "pebble";
		
		@Override
		public Reader getReader(String cacheKey) throws LoaderException {
			String path = templatePath + '/' + cacheKey;
			if (cacheKey.indexOf('.') == -1) {
				path += '.' + extension;
			}
			byte[] bytes = readAllBytes(path);
			String template = new String(bytes, charset);
			return new StringReader(template);
		}

		@Override
		public void setCharset(String charset) {
			this.charset = Charset.forName(charset);
		}

		@Override
		public void setPrefix(String prefix) {
		}

		@Override
		public void setSuffix(String suffix) {
		}

		@Override
		public String resolveRelativePath(String relativePath, String anchorPath) {
			
			// TODO compute relative path
			return relativePath;
		}

		@Override
		public String createCacheKey(String templateName) {
			return templateName;
		}
		
	}
	
}