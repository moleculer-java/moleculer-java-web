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
package services.moleculer.web.common;

import java.util.List;
import java.util.Map;

import org.synchronoss.cloud.nio.multipart.MultipartContext;
import org.synchronoss.cloud.nio.multipart.MultipartUtils;
import org.synchronoss.cloud.nio.multipart.NioMultipartParser;
import org.synchronoss.cloud.nio.multipart.NioMultipartParserListener;
import org.synchronoss.cloud.nio.stream.storage.StreamStorage;

import services.moleculer.stream.PacketStream;

public class ParserListener implements NioMultipartParserListener {

	// --- VARIABLES ---
	
	protected final PacketStream stream;
	
	protected final MultipartContext context;
	
	protected NioMultipartParser parser;

	// --- CONSTRUCTOR ---
	
	public ParserListener(PacketStream stream, MultipartContext context) {
		this.stream = stream;
		this.context = context;
	}
	
	// --- EVENT HANDLERS ---
	
	@Override
	public final void onPartFinished(StreamStorage partBodyStreamStorage,
			Map<String, List<String>> headersFromPart) {
		if (!MultipartUtils.isFormField(headersFromPart, context)) {
			stream.transferFrom(partBodyStreamStorage.getInputStream()).then(in -> {
				closeParser();
			}).catchError(err -> {
				closeParser();
			});
		}
	}

	@Override
	public final void onError(String message, Throwable cause) {
		stream.sendError(cause);
	}

	@Override
	public final void onNestedPartStarted(Map<String, List<String>> headersFromParentPart) {
	}

	@Override
	public final void onNestedPartFinished() {
	}

	@Override
	public final void onAllPartsFinished() {
	}

	// --- CLOSE ---
	
	protected void closeParser() {
		if (parser != null) {
			try {
				parser.close();						
			} catch (Exception ignored) {
				
				// Do nothing
			}
		}
	}
	
	// --- SETTERS ---
	
	public void setParser(NioMultipartParser parser) {
		this.parser = parser;
	}

}