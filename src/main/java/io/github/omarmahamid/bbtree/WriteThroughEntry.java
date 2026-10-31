package io.github.omarmahamid.bbtree;

import java.util.AbstractMap;

final class WriteThroughEntry extends AbstractMap.SimpleEntry<Object, Object> {

	private final transient OrderedStore store;

	WriteThroughEntry(OrderedStore store, Object key, Object value) {
		super(key, value);
		this.store = store;
	}

	@Override
	public Object setValue(Object value) {
		this.store.writeThrough(getKey(), value);
		return super.setValue(value);
	}

}
