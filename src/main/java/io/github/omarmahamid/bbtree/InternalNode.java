package io.github.omarmahamid.bbtree;

import java.util.Arrays;

final class InternalNode extends Node {

	private final Node[] children;

	InternalNode(int capacity, boolean inlineWords) {
		super(capacity, inlineWords);
		this.children = new Node[capacity + 1];
	}

	Node childAt(int index) {
		return this.children[index];
	}

	int route(KeyOrder order, Object key) {
		var found = find(order, key);
		return found >= 0 ? found + 1 : -(found + 1);
	}

	int routeBelow(KeyOrder order, Object key) {
		var found = find(order, key);
		return found >= 0 ? found : -(found + 1);
	}

	Object lowFenceOfChild(int child, Object lowFence) {
		return child > 0 ? this.keys[child - 1] : lowFence;
	}

	Object highFenceOfChild(int child, Object highFence) {
		return child < this.size ? this.keys[child] : highFence;
	}

	void becomeRoot(KeyOrder order, Node left, Object separator, Node right) {
		this.children[0] = left;
		this.children[1] = right;
		writeKey(order, 0, separator);
		this.size = 1;
	}

	void adoptFirstChild(Node child) {
		this.children[0] = child;
	}

	void appendChild(KeyOrder order, Object separator, Node child) {
		writeKey(order, this.size, separator);
		this.size++;
		this.children[this.size] = child;
	}

	void insertSeparator(KeyOrder order, int slot, Object separator, Node rightChild) {
		var tail = this.size - slot;
		shiftKeys(slot, slot + 1, tail);
		System.arraycopy(this.children, slot + 1, this.children, slot + 2, tail);
		writeKey(order, slot, separator);
		this.children[slot + 1] = rightChild;
		this.size++;
	}

	void removeSeparator(int slot) {
		var last = this.size - 1;
		var tail = last - slot;
		shiftKeys(slot + 1, slot, tail);
		System.arraycopy(this.children, slot + 2, this.children, slot + 1, tail);
		this.keys[last] = null;
		this.children[this.size] = null;
		this.size = last;
	}

	InternalNode splitOff(KeyOrder order, int middle) {
		var right = new InternalNode(capacity(), this.words != null);
		right.inheritPrefixOffset(this);
		var movedKeys = this.size - middle - 1;
		right.copyKeysFrom(order, this, middle + 1, 0, movedKeys);
		System.arraycopy(this.children, middle + 1, right.children, 0, movedKeys + 1);
		right.size = movedKeys;
		clearKeys(middle, this.size);
		Arrays.fill(this.children, middle + 1, this.size + 1, null);
		this.size = middle;
		return right;
	}

	boolean mergeOrShareChildren(KeyOrder order, int separatorSlot, Object lowFence, Object highFence) {
		var leftLow = lowFenceOfChild(separatorSlot, lowFence);
		var rightHigh = highFenceOfChild(separatorSlot + 1, highFence);
		var separator = this.keys[separatorSlot];
		var left = this.children[separatorSlot];
		var right = this.children[separatorSlot + 1];
		if (left instanceof LeafNode leftLeaf && right instanceof LeafNode rightLeaf) {
			if (leftLeaf.size() + rightLeaf.size() <= leftLeaf.capacity()) {
				leftLeaf.absorb(order, rightLeaf);
				removeSeparator(separatorSlot);
				leftLeaf.fitPrefix(order, leftLow, rightHigh);
				return true;
			}
			var newSeparator = leftLeaf.shareWith(order, rightLeaf);
			replaceSeparatorAndFit(order, separatorSlot, newSeparator, leftLow, rightHigh);
			return false;
		}
		var leftInternal = (InternalNode) left;
		var rightInternal = (InternalNode) right;
		if (leftInternal.size() + rightInternal.size() + 1 <= leftInternal.capacity()) {
			leftInternal.absorb(order, separator, rightInternal);
			removeSeparator(separatorSlot);
			leftInternal.fitPrefix(order, leftLow, rightHigh);
			return true;
		}
		var newSeparator = leftInternal.shareWith(order, separator, rightInternal);
		replaceSeparatorAndFit(order, separatorSlot, newSeparator, leftLow, rightHigh);
		return false;
	}

	private void replaceSeparatorAndFit(KeyOrder order, int separatorSlot, Object separator, Object leftLow,
			Object rightHigh) {
		writeKey(order, separatorSlot, separator);
		this.children[separatorSlot].fitPrefix(order, leftLow, separator);
		this.children[separatorSlot + 1].fitPrefix(order, separator, rightHigh);
	}

	private void absorb(KeyOrder order, Object separator, InternalNode right) {
		writeKey(order, this.size, separator);
		copyKeysFrom(order, right, 0, this.size + 1, right.size);
		System.arraycopy(right.children, 0, this.children, this.size + 1, right.size + 1);
		this.size += right.size + 1;
	}

	private Object shareWith(KeyOrder order, Object separator, InternalNode right) {
		var leftTarget = (this.size + right.size) / 2;
		if (this.size > leftTarget) {
			var moved = this.size - leftTarget;
			right.shiftKeys(0, moved, right.size);
			System.arraycopy(right.children, 0, right.children, moved, right.size + 1);
			right.copyKeysFrom(order, this, leftTarget + 1, 0, moved - 1);
			right.writeKey(order, moved - 1, separator);
			System.arraycopy(this.children, leftTarget + 1, right.children, 0, moved);
			var newSeparator = this.keys[leftTarget];
			right.size += moved;
			clearKeys(leftTarget, this.size);
			Arrays.fill(this.children, leftTarget + 1, this.size + 1, null);
			this.size = leftTarget;
			return newSeparator;
		}
		if (this.size == leftTarget) {
			return separator;
		}
		var moved = leftTarget - this.size;
		writeKey(order, this.size, separator);
		copyKeysFrom(order, right, 0, this.size + 1, moved - 1);
		System.arraycopy(right.children, 0, this.children, this.size + 1, moved);
		var newSeparator = right.keys[moved - 1];
		var remaining = right.size - moved;
		right.shiftKeys(moved, 0, remaining);
		System.arraycopy(right.children, moved, right.children, 0, remaining + 1);
		right.clearKeys(remaining, right.size);
		Arrays.fill(right.children, remaining + 1, right.size + 1, null);
		right.size = remaining;
		this.size = leftTarget;
		return newSeparator;
	}

}
