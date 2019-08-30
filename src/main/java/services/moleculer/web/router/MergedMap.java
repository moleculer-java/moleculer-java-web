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
package services.moleculer.web.router;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class MergedMap implements Map<String, Object> {

	// --- VARIABLES ---

	protected final Map<String, Object> data;
	protected final Map<String, Object> messages;

	// --- CONSTRUCTOR ---

	public MergedMap(Map<String, Object> data, Map<String, Object> messages) {
		this.data = data;
		this.messages = messages;
	}

	// --- MAP METHODS ---

	@Override
	public int size() {
		return messages.size() + data.size();
	}

	@Override
	public boolean isEmpty() {
		return messages.isEmpty() && data.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return messages.containsKey(key) || data.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return messages.containsValue(value) || data.containsValue(value);
	}

	@Override
	public Object get(Object key) {
		Object ret = data.get(key);
		if (ret == null) {
			return messages.get(key);
		}
		return ret;
	}

	@Override
	public Object put(String key, Object value) {
		return data.put(key, value);
	}

	@Override
	public Object remove(Object key) {
		return data.remove(key);
	}

	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		data.putAll(m);
	}

	@Override
	public void clear() {
		data.clear();
	}

	@Override
	public Set<String> keySet() {
		LinkedHashSet<String> set = new LinkedHashSet<>((size() + 1) * 2);
		set.addAll(data.keySet());
		set.addAll(messages.keySet());
		return set;
	}

	@Override
	public Collection<Object> values() {
		ArrayList<Object> list = new ArrayList<>((size() + 1) * 2);
		list.addAll(data.values());
		list.addAll(messages.values());
		return list;
	}

	@Override
	public Set<java.util.Map.Entry<String, Object>> entrySet() {
		LinkedHashSet<java.util.Map.Entry<String, Object>> set = new LinkedHashSet<>((size() + 1) * 2);
		set.addAll(data.entrySet());
		set.addAll(messages.entrySet());
		return set;
	}

}