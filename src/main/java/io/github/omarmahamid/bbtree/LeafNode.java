package io.github.omarmahamid.bbtree;

import java.util.Arrays;

final class LeafNode extends Node {

	static final Object KEY_ONLY_VALUE = Boolean.TRUE;

	private final Object[] values;

	LeafNode(int capacity, boolean inlineWords, boolean storesValues) {
		super(capacity, inlineWords);
		this.values = storesValues ? new Object[capacity] : null;
	}

	Object valueAt(int slot) {
		return this.values == null ? KEY_ONLY_VALUE : this.values[slot];
	}

	void replaceValue(int slot, Object value) {
		if (this.values != null) {
			this.values[slot] = value;
		}
	}

	void insert(KeyOrder order, int slot, Object key, Object value) {
		var tail = this.size - slot;
		shiftKeys(slot, slot + 1, tail);
		if (this.values != null) {
			System.arraycopy(this.values, slot, this.values, slot + 1, tail);
			this.values[slot] = value;
		}
		writeKey(order, slot, key);
		this.size++;
	}

	void remove(int slot) {
		var last = this.size - 1;
		var tail = last - slot;
		shiftKeys(slot + 1, slot, tail);
		this.keys[last] = null;
		if (this.values != null) {
			System.arraycopy(this.values, slot + 1, this.values, slot, tail);
			this.values[last] = null;
		}
		this.size = last;
	}

	void removeAll() {
		clearKeys(0, this.size);
		clearValues(0, this.size);
		this.size = 0;
	}

	void appendSorted(KeyOrder order, Object[] sortedKeys, Object[] sortedValues, int from, int count) {
		System.arraycopy(sortedKeys, from, this.keys, this.size, count);
		if (this.values != null) {
			System.arraycopy(sortedValues, from, this.values, this.size, count);
		}
		this.size += count;
		encodeWords(order, this.size - count, this.size);
	}

	LeafNode splitOff(KeyOrder order, int from) {
		var right = new LeafNode(capacity(), this.words != null, this.values != null);
		right.inheritPrefixOffset(this);
		var moved = this.size - from;
		right.copyKeysFrom(order, this, from, 0, moved);
		if (this.values != null) {
			System.arraycopy(this.values, from, right.values, 0, moved);
		}
		right.size = moved;
		clearKeys(from, this.size);
		clearValues(from, this.size);
		this.size = from;
		return right;
	}

	void absorb(KeyOrder order, LeafNode right) {
		var moved = right.size;
		copyKeysFrom(order, right, 0, this.size, moved);
		if (this.values != null) {
			System.arraycopy(right.values, 0, this.values, this.size, moved);
		}
		this.size += moved;
	}

	Object shareWith(KeyOrder order, LeafNode right) {
		var leftTarget = (this.size + right.size) / 2;
		if (this.size > leftTarget) {
			var moved = this.size - leftTarget;
			right.shiftKeys(0, moved, right.size);
			right.copyKeysFrom(order, this, leftTarget, 0, moved);
			if (this.values != null) {
				System.arraycopy(right.values, 0, right.values, moved, right.size);
				System.arraycopy(this.values, leftTarget, right.values, 0, moved);
			}
			right.size += moved;
			clearKeys(leftTarget, this.size);
			clearValues(leftTarget, this.size);
			this.size = leftTarget;
		}
		else if (this.size < leftTarget) {
			var moved = leftTarget - this.size;
			copyKeysFrom(order, right, 0, this.size, moved);
			var remaining = right.size - moved;
			right.shiftKeys(moved, 0, remaining);
			if (this.values != null) {
				System.arraycopy(right.values, 0, this.values, this.size, moved);
				System.arraycopy(right.values, moved, right.values, 0, remaining);
			}
			right.clearKeys(remaining, right.size);
			right.clearValues(remaining, right.size);
			right.size = remaining;
			this.size = leftTarget;
		}
		return right.keyAt(0);
	}

	void exposeTo(LeafWindow window, Object lowFence, Object highFence) {
		window.show(this.keys, this.values, this.size, lowFence, highFence);
	}

	void snapshotTo(LeafWindow window, Object lowFence, Object highFence) {
		var count = boundedSize();
		var keyCopy = Arrays.copyOf(this.keys, count);
		var valueCopy = this.values == null ? null : Arrays.copyOf(this.values, count);
		window.show(keyCopy, valueCopy, count, lowFence, highFence);
	}

	private void clearValues(int from, int to) {
		if (this.values != null) {
			Arrays.fill(this.values, from, to, null);
		}
	}

}
