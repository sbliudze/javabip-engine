package org.bip.engine;

import java.util.Map;
import java.util.Set;

public interface MultiMap<K, V> {

	void clear();

	boolean containsKey(K key);

	boolean containsValue(V value);

	Set<Map.Entry<K, Set<V>>> entrySet();

	boolean equals(Object other);

	Set<V> get(K key);

	int hashCode();

	boolean isEmpty();

	Set<K> keySet();

	boolean put(K key, V value);

	boolean putAll(K key, Set<V> values);

	boolean remove(K key, V value);

	Set<V> removeAll(K key);

	int size();

	Set<V> values();
}
