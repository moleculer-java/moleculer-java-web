/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2017 Andras Berkes [andras.berkes@programmer.net]<br>
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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class LazyMap<K,V> implements Map<K,V> {

	// --- VALUE TO MAP CONVERTER ---

	protected final Consumer<Map<K,V>> converter;

	// --- CONSTRUCTOR ---

	public LazyMap(Consumer<Map<K,V>> converter) {
		this.converter = converter;
	}

	// --- CONVERT ---	 ---

	protected Map<K,V> converted;

	protected Map<K,V> map() {
		if (converted == null) {
			converted = new LinkedHashMap<K,V>(32);
			converter.accept(converted);
		}
		return converted;
	}

	// --- MAP FUNCTIONS ---

	public boolean equals(Object o) {
		return map().equals(o);
	}

	public boolean containsValue(Object value) {
		return map().containsValue(value);
	}

	public V get(Object key) {
		return map().get(key);
	}

	public int hashCode() {
		return map().hashCode();
	}

	public V getOrDefault(Object key, V defaultValue) {
		return map().getOrDefault(key, defaultValue);
	}

	public void clear() {
		map().clear();
	}

	public String toString() {
		return map().toString();
	}

	public Set<K> keySet() {
		return map().keySet();
	}

	public int size() {
		return map().size();
	}

	public boolean isEmpty() {
		return map().isEmpty();
	}

	public Collection<V> values() {
		return map().values();
	}

	public boolean containsKey(Object key) {
		return map().containsKey(key);
	}

	public V put(K key, V value) {
		return map().put(key, value);
	}

	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return map().entrySet();
	}

	public void forEach(BiConsumer<? super K, ? super V> action) {
		map().forEach(action);
	}

	public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
		map().replaceAll(function);
	}

	public void putAll(Map<? extends K, ? extends V> m) {
		map().putAll(m);
	}

	public V remove(Object key) {
		return map().remove(key);
	}

	public V putIfAbsent(K key, V value) {
		return map().putIfAbsent(key, value);
	}

	public boolean remove(Object key, Object value) {
		return map().remove(key, value);
	}

	public boolean replace(K key, V oldValue, V newValue) {
		return map().replace(key, oldValue, newValue);
	}

	public V replace(K key, V value) {
		return map().replace(key, value);
	}

	public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		return map().computeIfAbsent(key, mappingFunction);
	}

	public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		return map().computeIfPresent(key, remappingFunction);
	}

	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		return map().compute(key, remappingFunction);
	}

	public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		return map().merge(key, value, remappingFunction);
	}

	public Object clone() {
		return new LazyMap<K,V>(converter);
	}

}