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

import static services.moleculer.web.common.GatewayUtils.readAllBytes;
import static services.moleculer.web.common.GatewayUtils.getLastModifiedTime;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.datatree.Tree;

/**
 * Abstract superclass of all server-side template engines (HTML renderers).
 * 
 * @see DataTreeEngine
 * @see FreeMarkerEngine
 * @see JadeEngine
 * @see MustacheEngine
 * @see PebbleEngine
 * @see ThymeleafEngine
 */
public abstract class AbstractTemplateEngine {

	// --- COMMON VARIABLES ---

	protected String templatePath = "";

	protected String defaultExtension = "html";

	protected int writeBufferSize = 2048;

	protected Charset charset = StandardCharsets.UTF_8;

	protected boolean reloadable;

	protected ExecutorService executor;

	// --- TRANSFORM JSON TO HTML ---

	public abstract byte[] transform(String templatePath, Tree data) throws Exception;

	// --- ROOT PATH OF TEMPLATES ---

	public final String getTemplatePath() {
		return templatePath;
	}

	public void setTemplatePath(String templatePath) {
		String path = Objects.requireNonNull(templatePath);
		path = path.replace('\\', '/');
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		this.templatePath = path.trim();
	}

	// --- DEFAULT EXTENSION ---

	public final String getDefaultExtension() {
		return defaultExtension;
	}

	public void setDefaultExtension(String defaultExtension) {
		String ext = Objects.requireNonNull(defaultExtension);
		while (ext.startsWith(".")) {
			ext = ext.substring(1);
		}
		ext = ext.trim();
		if (ext.isEmpty()) {
			throw new IllegalArgumentException("Empty extension!");
		}
		this.defaultExtension = ext;
	}

	// --- WRITE BUFFER SIZE ---

	public final int getWriteBufferSize() {
		return writeBufferSize;
	}

	public void setWriteBufferSize(int writeBufferSize) {
		this.writeBufferSize = writeBufferSize < 1 ? 2048 : writeBufferSize;
	}

	// --- CHARACTER ENCODING OF TEMPLATES ---

	public final Charset getCharset() {
		return charset;
	}

	public void setCharset(Charset charset) {
		this.charset = Objects.requireNonNull(charset);
	}

	// --- ENABLE / DISABLE RELOADING ---

	public final boolean isReloadable() {
		return reloadable;
	}

	public void setReloadable(boolean reloadable) {
		this.reloadable = reloadable;
	}

	// --- OPTIONAL EXECUTOR (CAN BE NULL) ---

	public final ExecutorService getExecutor() {
		return executor;
	}

	public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	// --- PROTECTED UTILS ---

	protected static long getLastModifiedMillis(String parent, String name, String extension, boolean reloadable) {
		if (!reloadable) {
			return 1;
		}
		String resourcePath = getAbsolutePath(parent, name, extension);
		return getLastModifiedTime(resourcePath);
	}

	protected static String loadResource(String parent, String name, String extension, Charset charset) {
		String resourcePath = getAbsolutePath(parent, name, extension);
		return new String(readAllBytes(resourcePath), charset);
	}

	protected static String getAbsolutePath(String parent, String name, String extension) {
		String root = null;
		if (parent != null) {
			root = parent.replace('\\', '/');
			while (root.endsWith("/")) {
				root = root.substring(0, root.length() - 1);
			}
		}
		String path = name.replace('\\', '/');
		if (root != null && !root.isEmpty()) {
			while (path.startsWith("/")) {
				path = path.substring(1);
			}
		}
		String resourcePath;
		if (root != null) {
			if (root.endsWith('.' + extension)) {
				resourcePath = getAbsolutePath(root, path);
			} else {
				resourcePath = root + '/' + path;
			}
		} else {
			resourcePath = name;
		}
		if (resourcePath.indexOf('.') == -1) {
			if (extension.startsWith(".")) {
				resourcePath += extension;
			} else {
				resourcePath += '.' + extension;
			}
		}
		return resourcePath;
	}

	protected static String getAbsolutePath(String basePath, String relativePath) {
		try {
			if (relativePath != null) {
				if (relativePath.startsWith(".")) {

					// '../file'
					int i = 0;
					String tmpURL = basePath.substring(0, basePath.lastIndexOf('/'));
					while (relativePath.indexOf("..", i) != -1) {
						i += 3;
						tmpURL = tmpURL.substring(0, tmpURL.lastIndexOf('/'));
					}
					return tmpURL + '/' + relativePath.substring(i, relativePath.length());
				}
				if (relativePath.startsWith("/") || relativePath.indexOf(":/") != -1) {

					// '/directory/file'
					// 'c:\windows\file'
					return relativePath;
				}

				// 'directory/file'
				int i = basePath.lastIndexOf('/');
				if (i == -1) {
					return relativePath;
				}
				return basePath.substring(0, basePath.lastIndexOf('/')) + '/' + relativePath;
			}
			return basePath;
		} catch (Throwable t) {
			throw new IllegalArgumentException(t.getMessage());
		}
	}

}