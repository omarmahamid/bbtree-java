package io.github.omarmahamid.bbtree;

import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;

record SortedContents(Kind kind, Comparator<?> comparator, InlineKey<?> inlineKey, NodeConfig nodeConfig,
		Object[] keys, Object[] values) implements Serializable {

	enum Kind {

		MAP,

		CONCURRENT_MAP,

		SET,

		CONCURRENT_SET

	}

	@Serial
	private Object readResolve() {
		var order = new KeyOrder(this.comparator, this.inlineKey);
		return switch (this.kind) {
			case MAP -> new BBTreeMap<>(restoredTree(order, true));
			case SET -> new BBTreeSet<>(restoredTree(order, false));
			case CONCURRENT_MAP -> new ConcurrentBBTreeMap<>(restoredLatchedTree(order, true));
			case CONCURRENT_SET -> new ConcurrentBBTreeSet<>(restoredLatchedTree(order, false));
		};
	}

	private Tree restoredTree(KeyOrder order, boolean storesValues) {
		var tree = new Tree(order, this.nodeConfig, storesValues);
		tree.loadSorted(this.keys, this.values, this.keys.length);
		return tree;
	}

	private LatchedTree restoredLatchedTree(KeyOrder order, boolean storesValues) {
		var tree = new LatchedTree(order, this.nodeConfig, storesValues);
		tree.loadSorted(this.keys, this.values, this.keys.length);
		return tree;
	}

}
