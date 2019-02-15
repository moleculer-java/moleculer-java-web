package services.moleculer.web.servlet.response;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import services.moleculer.web.WebResponse;

public abstract class AbstractWebResponse implements WebResponse {

	// --- RESPONSE VARIABLES ---

	protected final HttpServletResponse rsp;	
	protected final ServletOutputStream out;
	protected final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Custom properties (for inter-middleware communication).
	 */
	protected HashMap<String, Object> properties;

	// --- CONSTRUCTOR ---

	protected AbstractWebResponse(HttpServletResponse rsp) throws IOException {
		this.rsp = rsp;
		this.out = (ServletOutputStream) rsp.getOutputStream();
	}
	
	// --- PUBLIC WEBRESPONSE METHODS ---

	/**
	 * Sets the status code for this response. This method is used to set the
	 * return status code when there is no error (for example, for the 200 or
	 * 404 status codes). This method preserves any cookies and other response
	 * headers. Valid status codes are those in the 2XX, 3XX, 4XX, and 5XX
	 * ranges. Other status codes are treated as container specific.
	 * 
	 * @param code
	 *            the status code
	 */
	@Override
	public void setStatus(int code) {
		rsp.setStatus(code);
	}

	/**
	 * Gets the current status code of this response.
	 * 
	 * @return the status code
	 */
	@Override
	public int getStatus() {
		return rsp.getStatus();
	}

	/**
	 * Sets a response header with the given name and value. If the header had
	 * already been set, the new value overwrites the previous one.
	 * 
	 * @param name
	 *            the name of the header
	 * @param value
	 *            the header value If it contains octet string, it should be
	 *            encoded according to RFC 2047
	 */
	@Override
	public void setHeader(String name, String value) {
		rsp.setHeader(name, value);
	}

	/**
	 * Returns the value of the specified response header as a String. If the
	 * response did not include a header of the specified name, this method
	 * returns null. If there are multiple headers with the same name, this
	 * method returns the first head in the response.
	 * 
	 * @param name
	 *            name a String specifying the header name
	 * 
	 * @return a String containing the value of the response header, or null if
	 *         the response does not have a header of that name
	 */
	@Override
	public String getHeader(String name) {
		return rsp.getHeader(name);
	}

	/**
	 * Writes b.length bytes of body from the specified byte array to the output
	 * stream.
	 * 
	 * @param bytes
	 *            the data
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	public void send(byte[] bytes) throws IOException {
		out.write(bytes);
		out.flush();
	}
	
	/**
	 * Completes the synchronous operation that was started on the request.
	 * 
	 * @return return true, if any resources are released
	 */
	@Override
	public boolean end() {
		if (closed.compareAndSet(false, true)) {
			try {
				out.close();
			} catch (Throwable ignored) {
			}
			return true;
		}
		return false;
	}
	
	// --- CUSTOM PROPERTIES ---

	/**
	 * Associates the specified value with the specified "name" in this
	 * WebResponse. If the WebResponse previously contained a mapping for the
	 * "name", the old value is replaced.
	 * 
	 * @param name
	 *            a "name" with which the specified value is to be associated
	 * @param value
	 *            value to be associated with the specified "name"
	 */
	@Override
	public void setProperty(String name, Object value) {
		if (properties == null) {
			properties = new HashMap<>();
		}
		properties.put(name, value);
	}

	/**
	 * Returns the value to which the specified "name" is mapped, or null if
	 * this WebResponse contains no mapping for the "name".
	 * 
	 * @param name
	 *            the "name" whose associated value is to be returned
	 * 
	 * @return the value to which the specified "name" is mapped, or null if
	 *         this WebResponse contains no mapping for the "name"
	 */
	@Override
	public Object getProperty(String name) {
		if (properties == null) {
			return null;
		}
		return properties.get(name);
	}
	
}
