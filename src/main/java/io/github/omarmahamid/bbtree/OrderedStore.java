package io.github.omarmahamid.bbtree;

import java.util.Map;

interface OrderedStore extends LeafSource {

	Object ABSENT = new Object();

	NodeConfig nodeConfig();

	boolean concurrent();

	void requireSearchableKey(Object key);

	Object get(Object key);

	Object put(Object key, Object value);

	Object putIfAbsent(Object key, Object value);

	Object replace(Object key, Object value);

	boolean replace(Object key, Object expected, Object replacement);

	Object remove(Object key);

	boolean remove(Object key, Object value);

	long size();

	void clear();

	void loadSorted(Object[] sortedKeys, Object[] sortedValues, int count);

	int modificationCount();

	Object writeThrough(Object key, Object value);

	default Map.Entry<Object, Object> iterationEntry(Object key, Object value) {
		return new WriteThroughEntry(this, key, value);
	}

}
