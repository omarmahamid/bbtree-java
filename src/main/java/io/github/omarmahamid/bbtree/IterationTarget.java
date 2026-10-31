package io.github.omarmahamid.bbtree;

enum IterationTarget {

	KEY,

	VALUE,

	ENTRY;

	Object project(OrderedStore store, Object key, Object value) {
		return switch (this) {
			case KEY -> key;
			case VALUE -> value;
			case ENTRY -> store.iterationEntry(key, value);
		};
	}

}
