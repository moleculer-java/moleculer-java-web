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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import freemarker.cache.NullCacheStorage;
import freemarker.cache.StrongCacheStorage;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.ObjectWrapper;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateDateModel;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNodeModel;
import freemarker.template.TemplateNumberModel;
import freemarker.template.TemplateScalarModel;
import freemarker.template.TemplateSequenceModel;
import freemarker.template.Version;
import io.datatree.Tree;
import services.moleculer.util.CheckedTree;

public class FreeMarkerEngine extends AbstractTemplateEngine {

	// --- VARIABLES ---

	protected Configuration configuration;

	protected FreeMarkerLoader loader = new FreeMarkerLoader();

	// --- CONSTRUCTOR ---

	public FreeMarkerEngine() {
		this(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
	}

	public FreeMarkerEngine(Version incompatibleImprovements) {
		configuration = new Configuration(incompatibleImprovements);
		configuration.setObjectWrapper(new FreeMarkerTreeWrapper());
		configuration.setTemplateLoader(loader);
		configuration.setLocalizedLookup(false);
		configuration.setLocale(Locale.ENGLISH);
		configuration.setEncoding(Locale.ENGLISH, "UTF-8");
		setReloadable(false);
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {
		StringWriter out = new StringWriter(writeBufferSize);
		configuration.getTemplate(templatePath).process(data, out);
		return out.toString().getBytes(charset);
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	@Override
	public void setCharset(Charset charset) {
		super.setCharset(charset);
		configuration.setEncoding(configuration.getLocale(), this.charset.name());
	}

	// --- ENABLE / DISABLE RELOADING ---

	public void setReloadable(boolean reloadable) {
		if (this.reloadable != reloadable) {
			super.setReloadable(this.reloadable);
			loader.reloadable = this.reloadable;
			if (this.reloadable) {
				configuration.setCacheStorage(new NullCacheStorage());
			} else {
				configuration.setCacheStorage(new StrongCacheStorage());
			}
		}
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

	// --- FREEMARKER CONFIGURATION ---

	public Configuration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(Configuration configuration) {
		this.configuration = Objects.requireNonNull(configuration);
	}

	// --- LOADER CLASS ---

	public static class FreeMarkerLoader implements TemplateLoader {

		// --- VARIABLES ---

		protected String templatePath = "";

		protected String extension = "html";

		protected boolean reloadable;

		// --- TEMPLATE LOADER IMPLEMENTATION ---

		@Override
		public Object findTemplateSource(String name) throws IOException {
			return getAbsolutePath(templatePath, name, extension);
		}

		@Override
		public long getLastModified(Object templateSource) {
			return getLastModifiedMillis(null, String.valueOf(templateSource), extension, reloadable);
		}

		@Override
		public Reader getReader(Object templateSource, String encoding) throws IOException {
			String template = loadResource(null, String.valueOf(templateSource), extension,
					Charset.forName(encoding));
			return new StringReader(template);
		}

		@Override
		public void closeTemplateSource(Object templateSource) throws IOException {

			// Nothing to do
		}

	}

	// --- ABSTRACT MODEL CLASS ---

	public static class FreeMarkerAbstractModel implements TemplateHashModel {

		// --- WRAPPED DATA STRUCTURE ---

		protected final Tree node;

		// --- CONSTRUCTOR ---

		protected FreeMarkerAbstractModel(Tree node) {
			this.node = node;
		}

		// --- HASH MODEL IMPLEMENTATION ---

		@Override
		public TemplateModel get(String key) throws TemplateModelException {
			return nodeToModel(node.get(key));
		}

		@Override
		public boolean isEmpty() throws TemplateModelException {
			return node.isEmpty();
		}

		// --- DATA CONVERTER ---

		protected static final TemplateModel nodeToModel(Tree node) {
			if (node == null || node.isNull()) {
				return null;
			}
			if (node.isMap()) {
				return new FreeMarkerTreeModel(node);
			}
			if (node.isEnumeration()) {
				return new FreeMarkerTreeSequenceModel(node);
			}
			Object value = node.asObject();
			if (value instanceof Boolean) {
				return new TemplateBooleanModel() {

					@Override
					public final boolean getAsBoolean() {
						return (Boolean) value;
					}

				};
			}
			if (value instanceof Number) {
				return new TemplateNumberModel() {

					@Override
					public final Number getAsNumber() {
						return (Number) value;
					}

				};
			}
			if (value instanceof Date) {
				return new TemplateDateModel() {

					@Override
					public final int getDateType() {
						return DATETIME;
					}

					@Override
					public final Date getAsDate() {
						return (Date) value;
					}
				};
			}
			return new TemplateScalarModel() {

				@Override
				public final String getAsString() {
					return node.asString();
				}

			};
		}

	}

	// --- TREE MODEL CLASS ---

	public static class FreeMarkerTreeModel extends FreeMarkerAbstractModel implements TemplateNodeModel {

		// --- CONSTRUCTOR ---

		protected FreeMarkerTreeModel(Tree node) {
			super(node);
		}

		// --- NODE MODEL IMPLEMENTATION ---

		@Override
		public String getNodeNamespace() throws TemplateModelException {
			return null;
		}

		@Override
		public String getNodeName() throws TemplateModelException {
			return node.getName();
		}

		@Override
		public String getNodeType() throws TemplateModelException {
			return node.getName();
		}

		@Override
		public TemplateNodeModel getParentNode() throws TemplateModelException {
			Tree parent = node.getParent();
			return parent == null ? null : new FreeMarkerTreeModel(parent);
		}

		@Override
		public TemplateSequenceModel getChildNodes() throws TemplateModelException {
			return new FreeMarkerTreeSequenceModel(node);
		}

	}

	// --- TREE SEQUENCE MODEL CLASS ---

	public static class FreeMarkerTreeSequenceModel extends FreeMarkerAbstractModel implements TemplateSequenceModel {

		// --- WRAPPED SEQUENCE ---

		protected Tree[] children;

		// --- CONSTRUCTOR ---

		protected FreeMarkerTreeSequenceModel(Tree node) {
			super(node);
			children = new Tree[node.size()];
			int i = 0;
			for (Tree child : node) {
				children[i++] = child;
			}
		}

		// --- SEQUENCE MODEL IMPLEMENTATION ---

		@Override
		public int size() throws TemplateModelException {
			return children.length;
		}

		@Override
		public TemplateModel get(int index) throws TemplateModelException {
			return nodeToModel(children[index]);
		}

	}

	// --- TREE WRAPPER CLASS ---

	public static class FreeMarkerTreeWrapper implements ObjectWrapper {

		@Override
		public TemplateModel wrap(Object obj) {
			if (obj == null) {
				return null;
			}
			if (obj instanceof Tree) {
				return new FreeMarkerTreeModel((Tree) obj);
			}
			if (obj instanceof TemplateModel) {
				return (TemplateModel) obj;
			}
			return new FreeMarkerTreeModel(new CheckedTree(obj));			
		}

	}

}