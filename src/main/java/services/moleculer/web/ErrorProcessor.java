/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2026 Andras Berkes [andras.berkes@programmer.net]<br>
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
package services.moleculer.web;

import services.moleculer.web.router.Route;

/**
 * Custom error handler of the API Gateway - the Java counterpart of the
 * route-level and global-level "onError" settings of the Node.js
 * moleculer-web. Set it per {@link Route} ({@code route.setOnError(...)}) or
 * for the whole gateway ({@code gateway.setOnError(...)}, which applies to
 * every route that has no handler of its own).
 * <p>
 * The handler is invoked instead of the default JSON error response, for
 * example when the invoked Action throws, when the request body cannot be
 * parsed, when the "beforeCall" / "afterCall" hook fails, or when the response
 * cannot be serialized. It receives the live {@link Throwable}, so it can
 * inspect the exception type and its "data" payload (see
 * {@code GatewayUtils.toMoleculerError(cause)} for the status code / type the
 * default response would use).
 * <p>
 * <b>Contract:</b> the handler MUST complete the response by calling
 * {@code rsp.end()} (synchronously, or later from an asynchronous callback);
 * otherwise the request is left unhandled and the client waits. If the handler
 * throws before it has written anything, the gateway logs the failure and sends
 * the default JSON error response for the <i>original</i> cause; if it throws
 * after it started writing, the (partial) response is ended as-is.
 * <p>
 * Example - a plain-text error page with the same status code the default
 * JSON response would have used:
 *
 * <pre>
 * gateway.setOnError((route, req, rsp, cause) -&gt; {
 *     MoleculerError error = GatewayUtils.toMoleculerError(cause);
 *     byte[] body = ("Error: " + error.getMessage()).getBytes(StandardCharsets.UTF_8);
 *     rsp.setStatus(error.getCode());
 *     rsp.setHeader("Content-Type", "text/plain; charset=utf-8");
 *     rsp.setHeader("Content-Length", Integer.toString(body.length));
 *     rsp.send(body);
 *     rsp.end();
 * });
 * </pre>
 */
@FunctionalInterface
public interface ErrorProcessor {

	/**
	 * Handles an error and sends the response to the client.
	 *
	 * @param route
	 *            the Route whose action failed, or {@code null} when the error
	 *            occurred outside of a route (eg. a connector-level failure
	 *            before the request was routed)
	 * @param req
	 *            the request; {@code null} only for connector-level failures
	 *            where the request could not be parsed
	 * @param rsp
	 *            the response to write; the handler must call
	 *            {@code rsp.end()} when finished
	 * @param cause
	 *            the error (never {@code null}); the CompletionException /
	 *            ExecutionException wrappers of the asynchronous call are
	 *            already unwrapped, so this is the exception the Action (or
	 *            the hook, parser, serializer) actually threw
	 *
	 * @throws Exception
	 *             any error (eg. an I/O error while writing the response); the
	 *             gateway logs it and falls back to the default error response
	 *             when nothing has been written yet
	 */
	void onError(Route route, WebRequest req, WebResponse rsp, Throwable cause) throws Exception;

}
