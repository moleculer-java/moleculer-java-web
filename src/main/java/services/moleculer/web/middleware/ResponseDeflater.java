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
package services.moleculer.web.middleware;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import services.moleculer.service.Name;
import services.moleculer.web.RequestProcessor;
import services.moleculer.web.WebRequest;
import services.moleculer.web.WebResponse;
import services.moleculer.web.common.HttpConstants;

/**
 * Compresses body of REST responses. Do not use it with ServeStatic middleware;
 * ServeStatic also compresses the data. Use it to compress the response of REST
 * services. Using this middleware reduces the performance, so use it only on
 * slow networks. Sample:
 * <pre>
 * restRoute.use(new ResponseDeflater(Deflater.BEST_SPEED));
 * </pre>
 */
@Name("Response Deflater")
public class ResponseDeflater extends HttpMiddleware implements HttpConstants {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(ResponseDeflater.class);

	// --- PROPERTIES ---

	protected int compressionLevel = Deflater.DEFAULT_COMPRESSION;
	protected int bufferSize = 1024;
	protected Set<String> compressedTypes = new HashSet<>(
			Arrays.asList(new String[] { "image", "audio", "video", "gzip" }));

	// --- CONSTRUCTORS ---

	public ResponseDeflater() {
	}

	public ResponseDeflater(int compressionLevel) {
		setCompressionLevel(compressionLevel);
	}

	public ResponseDeflater(int compressionLevel, int bufferSize) {
		setCompressionLevel(compressionLevel);
		setBufferSize(bufferSize);
	}

	// --- CREATE NEW PROCESSOR ---

	@Override
	public RequestProcessor install(RequestProcessor next, Tree config) {
		return new AbstractRequestProcessor(next) {

			/**
			 * Handles request of the HTTP client.
			 * 
			 * @param req
			 *            WebRequest object that contains the request the client
			 *            made of the ApiGateway
			 * @param rsp
			 *            WebResponse object that contains the response the
			 *            ApiGateway returns to the client
			 * 
			 * @throws Exception
			 *             if an input or output error occurs while the
			 *             ApiGateway is handling the HTTP request
			 */
			@Override
			public void service(WebRequest req, WebResponse rsp) throws Exception {

				// Is compression supported by the client?
				String acceptEncoding = req.getHeader(ACCEPT_ENCODING);
				boolean compressionSupported = acceptEncoding != null && acceptEncoding.contains(DEFLATE);

				// Already compressed?
				AtomicBoolean alreadyCompressed = new AtomicBoolean();

				// Compressor
				ByteArrayOutputStream buffer = new ByteArrayOutputStream(bufferSize);
				DeflaterOutputStream deflater = new DeflaterOutputStream(buffer, new Deflater(compressionLevel));

				// Invoke next handler / action
				next.service(req, new WebResponse() {

					AtomicBoolean finished = new AtomicBoolean();

					@Override
					public final void setStatus(int code) {
						rsp.setStatus(code);
					}

					@Override
					public final int getStatus() {
						return rsp.getStatus();
					}

					@Override
					public final void setHeader(String name, String value) {
						rsp.setHeader(name, value);
						if (compressionSupported && !alreadyCompressed.get()) {
							if (CONTENT_ENCODING.equals(name) && (value.contains(DEFLATE) || value.contains(GZIP))) {
								alreadyCompressed.set(true);
							} else if (CONTENT_TYPE.equals(name)) {
								for (String part : compressedTypes) {
									if (value.contains(part)) {
										alreadyCompressed.set(true);
										break;
									}
								}
							}
						}
					}

					@Override
					public final String getHeader(String name) {
						return rsp.getHeader(name);
					}

					@Override
					public final void send(byte[] bytes) throws IOException {
						if (compressionSupported && !alreadyCompressed.get()) {

							// Compress bytes
							deflater.write(bytes);

						} else {

							// Do not compress
							rsp.send(bytes);
						}
					}

					@Override
					public final boolean end() {
						if (finished.compareAndSet(false, true)) {
							boolean ok;
							try {
								if (compressionSupported && !alreadyCompressed.get()) {

									// Send compressed content
									deflater.finish();
									byte[] bytes = buffer.toByteArray();
									rsp.setHeader(CONTENT_ENCODING, DEFLATE);
									rsp.setHeader(CONTENT_LENGTH, Integer.toString(bytes.length));
									rsp.send(bytes);

								}
							} catch (Exception cause) {
								logger.error("Unable to send compressed content!", cause);
							} finally {
								ok = rsp.end();
							}
							return ok;
						}
						return false;
					}

					@Override
					public final void setProperty(String name, Object value) {
						rsp.setProperty(name, value);
					}

					@Override
					public final Object getProperty(String name) {
						return rsp.getProperty(name);
					}

					@Override
					public final Object getInternalObject() {
						return rsp.getInternalObject();
					}
					
				});

			}
		};
	}

	// --- DISABLE COMPRESSION ---

	public ResponseDeflater addCompressedType(String compressedMimeTypePart) {
		compressedTypes.add(compressedMimeTypePart);
		return this;
	}

	// --- PROPERTY GETTERS AND SETTERS ---

	/**
	 * @return the compressionLevel
	 */
	public int getCompressionLevel() {
		return compressionLevel;
	}

	/**
	 * @param compressionLevel
	 *            the compressionLevel to set
	 */
	public void setCompressionLevel(int compressionLevel) {
		this.compressionLevel = compressionLevel;
	}

	/**
	 * @return the bufferSize
	 */
	public int getBufferSize() {
		return bufferSize;
	}

	/**
	 * @param bufferSize
	 *            the bufferSize to set
	 */
	public void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}

	/**
	 * @return the compressedTypes
	 */
	public Set<String> getCompressedTypes() {
		return compressedTypes;
	}

	/**
	 * @param compressedMimeTypeParts
	 *            the compressedTypes to set
	 */
	public void setCompressedTypes(Set<String> compressedMimeTypeParts) {
		compressedTypes = Objects.requireNonNull(compressedMimeTypeParts);
	}

	/**
	 * @param compressedMimeTypeParts
	 *            the compressedTypes to set
	 */
	public void setCompressedTypes(String... compressedMimeTypeParts) {
		compressedTypes = new HashSet<>(Arrays.asList(compressedMimeTypeParts));
	}

}