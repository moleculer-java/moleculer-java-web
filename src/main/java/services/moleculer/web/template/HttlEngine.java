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
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import httl.Resource;
import httl.spi.loaders.AbstractLoader;
import httl.spi.loaders.resources.StringResource;
import io.datatree.Tree;

public class HttlEngine implements TemplateEngine {

	// --- VARIABLES ---

	protected boolean reloadable;

	protected int writeBufferSize = 2048;

	protected String templatePath = "";

	protected HttlLoader loader = new HttlLoader();
	
	// --- CONSTRUCTOR ---

	public HttlEngine() {
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {

		return null;
	}

	// --- ROOT PATH OF TEMPLATES ---

	public String getTemplatePath() {
		return templatePath;
	}

	@Override
	public void setTemplatePath(String templatePath) {
		this.templatePath = templatePath;
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	public Charset getCharset() {
		return Charset.forName(loader.charset);
	}

	@Override
	public void setCharset(Charset charset) {
		loader.charset = charset.name();
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
	}

	// --- EXTENSION OF TEMPLATES ---

	public String getExtension() {
		return loader.extension;
	}

	public void setExtension(String extension) {
		loader.extension = extension;
	}
	
	// --- LOADER ---

	protected static final class HttlLoader extends AbstractLoader {

		protected String charset = "UTF-8";
		
		protected String extension = "httl";
		
		@Override
		protected List<String> doList(String directory, String suffix) throws IOException {
			
			// Preload fetaure is unsupported
			return Collections.emptyList();
		}

		@Override
		protected boolean doExists(String name, Locale locale, String path) throws IOException {
			String file = path.indexOf('.') == -1 ? path + '.' + extension : path;
			return isReadable(file);
		}

		@Override
		protected Resource doLoad(String name, Locale locale, String encoding, String path) throws IOException {
			String file = path.indexOf('.') == -1 ? path + '.' + extension : path;
			byte[] bytes = readAllBytes(file);
			String source = new String(bytes, charset);
			return new StringResource(getEngine(), name, locale, charset, source);
		}

	}

}