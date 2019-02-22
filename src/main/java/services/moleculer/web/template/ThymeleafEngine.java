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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.thymeleaf.cache.StandardCacheManager;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.util.FastStringWriter;

import io.datatree.Tree;
import services.moleculer.web.template.thymeleaf.ThymeleafLoader;
import services.moleculer.web.template.thymeleaf.TreeContext;

public class ThymeleafEngine extends org.thymeleaf.TemplateEngine implements TemplateEngine {

	// --- VARIABLES ---

	protected Charset charset = StandardCharsets.UTF_8;

	protected int writeBufferSize = 2048;
	
	protected AbstractConfigurableTemplateResolver loader = new ThymeleafLoader();

	// --- CONSTRUCTOR ---
	
	public ThymeleafEngine() {
		
		// Set template loader
		setTemplateResolver(loader);
		
		// Set default properties
		loader.setSuffix(".html");
		loader.setCharacterEncoding(charset.name());
		
		// Use larger caches
		StandardCacheManager cacheManager = new StandardCacheManager();
		cacheManager.setTemplateCacheInitialSize(512);
		cacheManager.setTemplateCacheMaxSize(2048);
		cacheManager.setExpressionCacheInitialSize(512);
		cacheManager.setExpressionCacheMaxSize(2048);
		setCacheManager(cacheManager);
	}
	
	// --- TRANSFORM JSON TO HTML ---
	
	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		FastStringWriter writer = new FastStringWriter(writeBufferSize);
		process(templatePath, new TreeContext(data), writer);
		return writer.toString().getBytes(charset);
	}

	// --- ROOT PATH OF TEMPLATES ---

	public String getTemplatePath() {
		return loader.getPrefix();
	}
	
	@Override
	public void setTemplatePath(String templatePath) {
		loader.setPrefix(templatePath);
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	public Charset getCharset() {
		return Charset.forName(loader.getCharacterEncoding());
	}

	@Override
	public void setCharset(Charset charset) {
		loader.setCharacterEncoding(charset.name());
	}

	// --- ENABLE / DISABLE RELOADING ---

	public boolean isReloadable() {
		return !loader.isCacheable();
	}
	
	@Override
	public void setReloadable(boolean reloadable) {
		loader.setCacheable(!reloadable);
	}

	// --- TEMPLATE LOADER
	
	public AbstractConfigurableTemplateResolver getLoader() {
		return loader;
	}

	public void setLoader(AbstractConfigurableTemplateResolver loader) {
		this.loader = loader;
	}

}