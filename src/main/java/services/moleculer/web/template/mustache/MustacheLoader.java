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
package services.moleculer.web.template.mustache;

import static services.moleculer.web.common.GatewayUtils.readAllBytes;

import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.github.mustachejava.MustacheResolver;

public class MustacheLoader implements MustacheResolver {

	// --- VARIABLES ---

	protected Charset charset = StandardCharsets.UTF_8;
	
	protected String templatePath = "";
	
	// --- LOADER METHOD ---
	
	@Override
	public Reader getReader(String name) {
		String path = templatePath + '/' + name.replace('\\', '/');
		byte[] bytes = readAllBytes(path);
		String template = new String(bytes, charset);
		return new StringReader(template);
	}

	// --- GETTERS / SETTERS ---

	public Charset getCharset() {
		return charset;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}
	
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
