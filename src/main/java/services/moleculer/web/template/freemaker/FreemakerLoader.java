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
package services.moleculer.web.template.freemaker;

import static services.moleculer.web.common.GatewayUtils.getLastModifiedTime;
import static services.moleculer.web.common.GatewayUtils.isReadable;
import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import freemarker.cache.TemplateLoader;

public class FreemakerLoader implements TemplateLoader {

	// --- VARIABLES ---
	
	protected String templatePath = "";
	
	// --- TEMPLATE LOADER IMPLEMENTATION ---
	
	@Override
	public Object findTemplateSource(String name) throws IOException {
		String path = templatePath + '/' + name.replace('\\', '/');
		if (isReadable(path)) {
			return path;
		}
		return null;
	}

	@Override
	public long getLastModified(Object templateSource) {
		return getLastModifiedTime(String.valueOf(templateSource));
	}

	@Override
	public Reader getReader(Object templateSource, String encoding) throws IOException {
		String name = String.valueOf(templateSource);
		if (!isReadable(name)) {
			throw new IOException("File not found:" + name);
		}
		byte[] bytes = readAllBytes(name);
		String template = new String(bytes, encoding);
		return new StringReader(template);
	}

	@Override
	public void closeTemplateSource(Object templateSource) throws IOException {
		
		// Nothing to do
	}

	// --- GETTERS AND SETTERS ---

	public void setTemplatePath(String templatePath) {
		templatePath = templatePath.replace('\\', '/');
		while (templatePath.endsWith("/")) {
			templatePath = templatePath.substring(0, templatePath.length() - 1);
		}
		this.templatePath = templatePath;
	}

	public String getTemplatePath() {
		return templatePath;
	}
	
}
