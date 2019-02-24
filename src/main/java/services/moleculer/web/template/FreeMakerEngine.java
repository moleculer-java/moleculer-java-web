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
import java.util.Date;

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

public class FreeMakerEngine extends Configuration implements TemplateEngine {

	// --- VARIABLES ---

	protected boolean reloadable;

	protected int writeBufferSize = 2048;

	protected FreeMakerLoader loader = new FreeMakerLoader();

	protected Charset charset = StandardCharsets.UTF_8;

	// --- CONSTRUCTOR ---

	public FreeMakerEngine() {
		this(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
	}

	public FreeMakerEngine(Version incompatibleImprovements) {
		super(incompatibleImprovements);
		setObjectWrapper(new FreeMakerTreeWrapper());
		setTemplateLoader(loader);
		setReloadable(false);
		setLocalizedLookup(false);
		setEncoding(getLocale(), "UTF-8");
	}

	// --- TRANSFORM JSON TO HTML ---

	@Override
	public byte[] transform(String templatePath, Tree data) throws Exception {

		// Render template
		StringWriter out = new StringWriter(writeBufferSize);
		getTemplate(templatePath).process(data, out);
		return out.toString().getBytes(charset);
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	public Charset getCharset() {
		charset = Charset.forName(getEncoding(getLocale()));
		return charset;
	}

	@Override
	public void setCharset(Charset charset) {
		if (!this.charset.equals(charset)) {
			setEncoding(getLocale(), charset.name());
			this.charset = charset;
		}
	}

	// --- ENABLE / DISABLE RELOADING ---

	public boolean isReloadable() {
		return reloadable;
	}

	public void setReloadable(boolean reloadable) {
		if (this.reloadable != reloadable) {
			this.reloadable = reloadable;
			if (reloadable) {
				setCacheStorage(new NullCacheStorage());
			} else {
				setCacheStorage(new StrongCacheStorage());
			}
		}
	}

	// --- ROOT PATH OF TEMPLATES ---

	public String getTemplatePath() {
		return loader.getTemplatePath();
	}

	@Override
	public void setTemplatePath(String templatePath) {
		loader.setTemplatePath(templatePath);
	}

	// --- INITIAL SIZE OF WRITE BUFFER ---

	public int getWriteBufferSize() {
		return writeBufferSize;
	}

	public void setWriteBufferSize(int writeBufferSize) {
		this.writeBufferSize = writeBufferSize;
	}

	// --- ABSTRACT MODEL CLASS ---
	
	protected static class FreeMakerAbstractModel implements TemplateHashModel {

		// --- WRAPPED DATA STRUCTURE ---

		protected final Tree node;

		// --- CONSTRUCTOR ---

		protected FreeMakerAbstractModel(Tree node) {
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
				return new FreeMakerTreeModel(node);
			}
			if (node.isEnumeration()) {
				return new FreeMakerTreeSequenceModel(node);
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
	
	// --- LOADER CLASS ---
	
	protected static class FreeMakerLoader implements TemplateLoader {

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

		protected void setTemplatePath(String templatePath) {
			templatePath = templatePath.replace('\\', '/');
			while (templatePath.endsWith("/")) {
				templatePath = templatePath.substring(0, templatePath.length() - 1);
			}
			this.templatePath = templatePath;
		}

		protected String getTemplatePath() {
			return templatePath;
		}
		
	}

	// --- TREE MODEL CLASS ---
	
	protected static class FreeMakerTreeModel extends FreeMakerAbstractModel implements TemplateNodeModel {

		// --- CONSTRUCTOR ---

		protected FreeMakerTreeModel(Tree node) {
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
			return parent == null ? null : new FreeMakerTreeModel(parent);
		}

		@Override
		public TemplateSequenceModel getChildNodes() throws TemplateModelException {
			return new FreeMakerTreeSequenceModel(node);
		}

	}
	
	// --- TREE SEQUENCE MODEL CLASS ---
	
	protected static class FreeMakerTreeSequenceModel extends FreeMakerAbstractModel implements TemplateSequenceModel {

		// --- WRAPPED SEQUENCE ---

		protected Tree[] children;

		// --- CONSTRUCTOR ---

		protected FreeMakerTreeSequenceModel(Tree node) {
			super(node);
			Tree[] children = new Tree[node.size()];
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
	
	protected static class FreeMakerTreeWrapper implements ObjectWrapper {

		@Override
		public TemplateModel wrap(Object obj) {
			return new FreeMakerTreeModel((Tree) obj);
		}
		
	}
	
}