package org.bip.engine.dynamicity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class MultiHashMap<K, V> implements MultiMap<K, V> {
	private Map<K, Set<V>> internal = new HashMap<K, Set<V>>();

	@Override
	public void clear() {
		internal.clear();
	}

	@Override
	public boolean containsKey(K key) {
		return internal.containsKey(key);
	}

	@Override
	public boolean containsValue(V value) {
		for (Set<V> set : internal.values()) {
			if (set.contains(value)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Set<Entry<K, Set<V>>> entrySet() {
		return internal.entrySet();
	}

	@Override
	public Set<V> get(K key) {
		return internal.get(key);
	}

	@Override
	public boolean isEmpty() {
		return internal.isEmpty();
	}

	@Override
	public Set<K> keySet() {
		return internal.keySet();
	}

	@Override
	public boolean put(K key, V value) {
		Set<V> values = internal.get(key);
		if (values == null) {
			values = new HashSet<V>();
			values.add(value);
			internal.put(key, values);
			return true;
		} else {
			return values.add(value);
		}
	}

	@Override
	public boolean putAll(K key, Set<V> values) {
		Set<V> knownValues = internal.get(key);
		if (knownValues == null) {
			return !internal.put(key, values).isEmpty();
		} else {
			return values.addAll(values);
		}
	}

	@Override
	public boolean remove(K key, V value) {
		Set<V> values = internal.get(key);
		if (values == null) {
			return false;
		}
		return values.remove(value);
	}

	@Override
	public Set<V> removeAll(K key) {
		return internal.remove(key);
	}

	@Override
	public int size() {
		return internal.size();
	}

	@Override
	public Set<V> values() {
		Set<V> allValues = new HashSet<V>();
		for (Set<V> set : internal.values()) {
			allValues.addAll(set);
		}
		return allValues;
	}
}
