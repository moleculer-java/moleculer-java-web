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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.cache.StandardCacheManager;
import org.thymeleaf.context.IContext;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.templateresource.StringTemplateResource;
import org.thymeleaf.util.FastStringWriter;

import io.datatree.Tree;

/**
 * Server-side template engine based on Thymeleaf API. Required dependency:
 * 
 * <pre>
 * // https://mvnrepository.com/artifact/org.thymeleaf/thymeleaf
 * compile group: 'org.thymeleaf', name: 'thymeleaf', version: '3.0.11.RELEASE'
 * </pre>
 * 
 * @see DataTreeEngine
 * @see FreeMarkerEngine
 * @see JadeEngine
 * @see MustacheEngine
 * @see PebbleEngine
 */
public class ThymeleafEngine extends AbstractTemplateEngine {

	// --- VARIABLES ---

	protected TemplateEngine engine = new TemplateEngine();

	protected AbstractConfigurableTemplateResolver loader = new ThymeleafLoader();

	// --- CONSTRUCTOR ---

	public ThymeleafEngine() {

		// Set template loader
		engine.setTemplateResolver(loader);

		// Set default properties
		loader.setSuffix(".html");
		loader.setCharacterEncoding(charset.name());

		// Enable caching (and disable reloading)
		setReloadable(false);
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		FastStringWriter writer = new FastStringWriter(writeBufferSize);
		engine.process(templatePath, new TreeContext(data), writer);
		return writer.toString().getBytes(charset);
	}

	// --- ROOT PATH OF TEMPLATES ---

	@Override
	public void setTemplatePath(String templatePath) {
		super.setTemplatePath(templatePath);
		loader.setPrefix(this.templatePath);
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	@Override
	public void setCharset(Charset charset) {
		super.setCharset(charset);
		loader.setCharacterEncoding(this.charset.name());
	}

	// --- ENABLE / DISABLE RELOADING ---

	@Override
	public void setReloadable(boolean reloadable) {
		if (this.reloadable != reloadable) {
			super.setReloadable(reloadable);
			loader.setCacheable(!this.reloadable);
			if (this.reloadable) {
				loader.setCacheable(false);
				engine.setCacheManager(null);
			} else {
				loader.setCacheable(true);
				StandardCacheManager cacheManager = new StandardCacheManager();
				cacheManager.setTemplateCacheInitialSize(512);
				cacheManager.setTemplateCacheMaxSize(2048);
				cacheManager.setExpressionCacheInitialSize(512);
				cacheManager.setExpressionCacheMaxSize(2048);
				engine.setCacheManager(cacheManager);
			}
		}
	}

	// --- DEFAULT EXTENSION ---

	@Override
	public void setDefaultExtension(String defaultExtension) {
		super.setDefaultExtension(defaultExtension);
		loader.setSuffix('.' + this.defaultExtension);
	}

	// --- THYMELEAF ENGINE ---

	public TemplateEngine getEngine() {
		return engine;
	}

	public void setEngine(TemplateEngine engine) {
		this.engine = Objects.requireNonNull(engine);
	}

	// --- LOADER CLASS ---

	public static class ThymeleafLoader extends AbstractConfigurableTemplateResolver {

		@Override
		protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration, String ownerTemplate,
				String template, String resourceName, String characterEncoding,
				Map<String, Object> templateResolutionAttributes) {
			String source = loadResource(getPrefix(), template, getSuffix(), Charset.forName(characterEncoding));
			return new StringTemplateResource(source);
		}

	}

	// --- CONTEXT CLASS ---

	public static class TreeContext implements IContext {

		// --- LOCALE CACHE ---

		protected static final ConcurrentHashMap<String, Locale> localeCache = new ConcurrentHashMap<>();

		// --- VARIABLES ---

		protected final Tree data;

		protected final HashMap<String, Object> variables = new HashMap<>();

		protected Locale locale;

		// --- CONSTRUCTOR ---

		public TreeContext(Tree data) {
			this.data = data;
			collectVariables(data);
		}

		// --- CONTEXT IMPLEMENTATION ---

		@Override
		public Locale getLocale() {
			if (locale == null) {
				Tree meta = data.getMeta(false);
				if (meta != null) {
					String loc = meta.get("$locale", (String) null);
					if (loc != null) {
						locale = localeCache.get(loc);
						if (locale == null) {
							locale = Locale.forLanguageTag(loc);
							localeCache.put(loc, locale);
						}
					}
				}
				if (locale == null) {
					return Locale.ENGLISH;
				}
			}
			return locale;
		}

		@Override
		public boolean containsVariable(String name) {
			return variables.containsKey(name);
		}

		@Override
		public Set<String> getVariableNames() {
			return variables.keySet();
		}

		@Override
		public Object getVariable(String name) {
			return variables.get(name);
		}

		// --- UTILITIES ---

		protected void collectVariables(Tree root) {
			if (root != null) {
				for (Tree child : root) {
					variables.put(child.getPath(), child.asObject());
					if (child.isMap()) {
						collectVariables(child);
					}
				}
			}
		}

	}

}