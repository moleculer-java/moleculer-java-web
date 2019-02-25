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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheResolver;

import io.datatree.Tree;

public class MustacheEngine implements TemplateEngine {

	// --- VARIABLES ---

	protected boolean reloadable;

	protected int writeBufferSize = 2048;

	protected DefaultMustacheFactory mustache;

	protected MustacheLoader loader = new MustacheLoader();

	// --- CONSTRUCTOR ---

	public MustacheEngine() {
		mustache = new DefaultMustacheFactory(loader);
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {

		// Always load templates
		if (reloadable) {
			StringWriter out = new StringWriter(writeBufferSize);
			Reader reader = loader.getReader(templatePath);
			Mustache template = mustache.compile(reader, templatePath);
			template.execute(out, data.asObject());
			return out.toString().getBytes(loader.charset);
		}

		// Use cache
		Mustache template = mustache.compile(templatePath);
		StringWriter out = new StringWriter(writeBufferSize);
		template.execute(out, data.asObject());
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

	// --- ENABLE / DISABLE RELOADING ---

	public boolean isReloadable() {
		return reloadable;
	}

	public void setReloadable(boolean reloadable) {
		this.reloadable = reloadable;
	}

	// --- INITIAL SIZE OF WRITE BUFFER ---

	public int getWriteBufferSize() {
		return writeBufferSize;
	}

	public void setWriteBufferSize(int writeBufferSize) {
		this.writeBufferSize = writeBufferSize;
	}

	// --- EXTENSION OF TEMPLATES ---

	public String getExtension() {
		return loader.extension;
	}

	public void setExtension(String extension) {
		loader.extension = extension;
	}
	
	// --- LOADER CLASS ---

	protected static class MustacheLoader implements MustacheResolver {

		// --- VARIABLES ---

		protected Charset charset = StandardCharsets.UTF_8;

		protected String templatePath = "";

		protected String extension = "mustache";

		// --- LOADER METHOD ---

		@Override
		public Reader getReader(String name) {
			String path = templatePath + '/' + name;
			if (name.indexOf('.') == -1) {
				path += '.' + extension;
			}
			byte[] bytes = readAllBytes(path);
			String template = new String(bytes, charset);
			return new StringReader(template);
		}

	}

}