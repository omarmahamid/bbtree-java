package io.github.omarmahamid.bbtree;

final class LeafWindow {

	private static final Object[] NO_KEYS = new Object[0];

	private Object[] keys = NO_KEYS;

	private Object[] values;

	private int size;

	private Object lowFence = Node.NO_FENCE;

	private Object highFence = Node.NO_FENCE;

	void show(Object[] keys, Object[] values, int size, Object lowFence, Object highFence) {
		this.keys = keys;
		this.values = values;
		this.size = size;
		this.lowFence = lowFence;
		this.highFence = highFence;
	}

	void showNothing() {
		show(NO_KEYS, null, 0, Node.NO_FENCE, Node.NO_FENCE);
	}

	int size() {
		return this.size;
	}

	Object keyAt(int slot) {
		return this.keys[slot];
	}

	Object valueAt(int slot) {
		return this.values == null ? LeafNode.KEY_ONLY_VALUE : this.values[slot];
	}

	Object lowFence() {
		return this.lowFence;
	}

	Object highFence() {
		return this.highFence;
	}

	int find(KeyOrder order, Object key) {
		return order.binarySearch(this.keys, 0, this.size, key);
	}

}
