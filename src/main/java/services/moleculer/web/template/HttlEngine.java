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

import static services.moleculer.web.common.GatewayUtils.isReadable;
import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import httl.Resource;
import httl.Template;
import httl.spi.engines.DefaultEngine;
import httl.spi.loaders.AbstractLoader;
import httl.spi.loaders.resources.StringResource;
import io.datatree.Tree;

public class HttlEngine extends DefaultEngine implements TemplateEngine {

	// --- VARIABLES ---

	protected boolean reloadable;

	protected Charset charset = StandardCharsets.UTF_8;

	protected int writeBufferSize = 2048;

	protected String templatePath = "";

	// --- CONSTRUCTOR ---

	public HttlEngine() {
		
		// Set default properties
		setPreload(false);
		setLocalized(false);
		setReloadable(false);
		setLoader(new HttlLoader());
		setTemplateSuffix(new String[] { ".httl", ".html" });
		
		setLogger(new httl.spi.loggers.Log4jLogger());
		setCache(new java.util.concurrent.ConcurrentHashMap<Object, Object>());
		setTranslator(new httl.spi.translators.CompiledTranslator());
		setResolver(new httl.spi.resolvers.SystemResolver());
		setTemplateParser(new httl.spi.parsers.TemplateParser());
		
		setMapConverter(new httl.spi.converters.BeanMapConverter());
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {

		// Get template
		Template template = getTemplate(templatePath);

		// Render template
		StringWriter out = new StringWriter(writeBufferSize);
		template.render(data.asObject(), out);
		return out.toString().getBytes(charset);
	}

	// --- ROOT PATH OF TEMPLATES ---

	public String getTemplatePath() {
		return templatePath;
	}

	@Override
	public void setTemplatePath(String templatePath) {
		this.templatePath = templatePath;
		setTemplateDirectory(templatePath);
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	public Charset getCharset() {
		return charset;
	}

	@Override
	public void setCharset(Charset charset) {
		this.charset = charset;
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
		this.reloadable = reloadable;
		super.setReloadable(reloadable);
	}

	// --- LOADER ---

	protected static final class HttlLoader extends AbstractLoader {

		@Override
		protected List<String> doList(String directory, String suffix) throws IOException {
			
			// Preload fetaure is unsupported
			return Collections.emptyList();
		}

		@Override
		protected boolean doExists(String name, Locale locale, String path) throws IOException {
			return isReadable(path);
		}

		@Override
		protected Resource doLoad(String name, Locale locale, String encoding, String path) throws IOException {
			byte[] bytes = readAllBytes(path);
			String source = new String(bytes, encoding);
			return new StringResource(getEngine(), name, locale, encoding, source);
		}

	}

}