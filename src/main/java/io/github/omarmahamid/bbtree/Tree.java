package io.github.omarmahamid.bbtree;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

final class Tree implements OrderedStore {

	private static final int MAXIMUM_HEIGHT = 64;

	private final KeyOrder order;

	private final NodeFactory nodes;

	private final Node[] pathNodes = new Node[MAXIMUM_HEIGHT];

	private final int[] pathChildren = new int[MAXIMUM_HEIGHT];

	private final Object[] pathLowFences = new Object[MAXIMUM_HEIGHT];

	private final Object[] pathHighFences = new Object[MAXIMUM_HEIGHT];

	private Node root;

	private int size;

	private int modificationCount;

	Tree(KeyOrder order, NodeConfig config, boolean storesValues) {
		this.order = order;
		this.nodes = new NodeFactory(config, order, storesValues);
	}

	static Tree withEntries(KeyOrder order, Map<?, ?> entries) {
		var tree = new Tree(order, NodeConfig.defaults(), true);
		tree.insertAll(entries);
		return tree;
	}

	static Tree withElements(KeyOrder order, Collection<?> elements) {
		var tree = new Tree(order, NodeConfig.defaults(), false);
		tree.insertAll(elements);
		return tree;
	}

	void insertAll(Map<?, ?> entries) {
		var presorted = size() == 0 ? SortedEntries.presortedIn(entries, this.order.comparator()) : null;
		if (presorted != null) {
			loadSorted(presorted.keys(), presorted.values(), presorted.count());
			return;
		}
		for (var entry : entries.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	boolean insertAll(Collection<?> elements) {
		var presorted = size() == 0 ? SortedEntries.presortedIn(elements, this.order.comparator()) : null;
		if (presorted != null) {
			loadSorted(presorted.keys(), null, presorted.count());
			return true;
		}
		var changed = false;
		for (var element : elements) {
			changed |= putIfAbsent(element, LeafNode.KEY_ONLY_VALUE) == ABSENT;
		}
		return changed;
	}

	@Override
	public KeyOrder order() {
		return this.order;
	}

	@Override
	public NodeConfig nodeConfig() {
		return this.nodes.config();
	}

	@Override
	public boolean concurrent() {
		return false;
	}

	@Override
	public void requireSearchableKey(Object key) {
		this.order.requireSearchable(key);
	}

	Node root() {
		return this.root;
	}

	Tree emptyCopy() {
		return new Tree(this.order.unresolvedCopy(), this.nodes.config(), this.nodes.storesValues());
	}

	Tree copy() {
		var copy = emptyCopy();
		var entries = SortedEntries.capturedFrom(this, KeyRange.UNBOUNDED, false);
		copy.loadSorted(entries.keys(), entries.values(), entries.count());
		return copy;
	}

	@Override
	public void loadSorted(Object[] sortedKeys, Object[] sortedValues, int count) {
		this.root = this.nodes.buildSorted(sortedKeys, sortedValues, count);
		this.size = count;
		this.modificationCount++;
	}

	@Override
	public Object get(Object key) {
		this.order.requireSearchable(key);
		var node = this.root;
		if (node == null) {
			return ABSENT;
		}
		while (node instanceof InternalNode internal) {
			node = internal.childAt(internal.route(this.order, key));
		}
		var leaf = (LeafNode) node;
		var slot = leaf.find(this.order, key);
		return slot >= 0 ? leaf.valueAt(slot) : ABSENT;
	}

	@Override
	public Object put(Object key, Object value) {
		return insert(key, value, true);
	}

	@Override
	public Object putIfAbsent(Object key, Object value) {
		return insert(key, value, false);
	}

	@Override
	public Object replace(Object key, Object value) {
		this.order.requireSearchable(key);
		if (this.root == null) {
			return ABSENT;
		}
		var leaf = (LeafNode) this.pathNodes[descendRecordingPath(key)];
		var slot = leaf.find(this.order, key);
		if (slot < 0) {
			return ABSENT;
		}
		var previous = leaf.valueAt(slot);
		leaf.replaceValue(slot, value);
		return previous;
	}

	@Override
	public boolean replace(Object key, Object expected, Object replacement) {
		this.order.requireSearchable(key);
		if (this.root == null) {
			return false;
		}
		var leaf = (LeafNode) this.pathNodes[descendRecordingPath(key)];
		var slot = leaf.find(this.order, key);
		if (slot < 0 || !Objects.equals(leaf.valueAt(slot), expected)) {
			return false;
		}
		leaf.replaceValue(slot, replacement);
		return true;
	}

	@Override
	public Object remove(Object key) {
		this.order.requireSearchable(key);
		if (this.root == null) {
			return ABSENT;
		}
		var depth = descendRecordingPath(key);
		var leaf = (LeafNode) this.pathNodes[depth];
		var slot = leaf.find(this.order, key);
		if (slot < 0) {
			return ABSENT;
		}
		var previous = leaf.valueAt(slot);
		removeAt(depth, leaf, slot);
		return previous;
	}

	@Override
	public boolean remove(Object key, Object value) {
		this.order.requireSearchable(key);
		if (this.root == null) {
			return false;
		}
		var depth = descendRecordingPath(key);
		var leaf = (LeafNode) this.pathNodes[depth];
		var slot = leaf.find(this.order, key);
		if (slot < 0 || !Objects.equals(leaf.valueAt(slot), value)) {
			return false;
		}
		removeAt(depth, leaf, slot);
		return true;
	}

	@Override
	public long size() {
		return this.size;
	}

	@Override
	public void clear() {
		this.root = null;
		this.size = 0;
		this.modificationCount++;
		forgetPath();
	}

	@Override
	public int modificationCount() {
		return this.modificationCount;
	}

	@Override
	public Object writeThrough(Object key, Object value) {
		var previous = replace(key, value);
		if (previous == ABSENT) {
			throw new IllegalStateException("The entry was removed from the map");
		}
		return previous;
	}

	@Override
	public void loadLeaf(LeafWindow window, Object key, Routing routing) {
		var node = this.root;
		if (node == null) {
			window.showNothing();
			return;
		}
		var lowFence = Node.NO_FENCE;
		var highFence = Node.NO_FENCE;
		while (node instanceof InternalNode internal) {
			var child = routing.childFor(internal, this.order, key);
			lowFence = internal.lowFenceOfChild(child, lowFence);
			highFence = internal.highFenceOfChild(child, highFence);
			node = internal.childAt(child);
		}
		((LeafNode) node).exposeTo(window, lowFence, highFence);
	}

	private Object insert(Object key, Object value, boolean replaceExisting) {
		this.order.requireSearchable(key);
		if (this.root == null) {
			insertIntoEmpty(key, value);
			return ABSENT;
		}
		var depth = descendRecordingPath(key);
		var leaf = (LeafNode) this.pathNodes[depth];
		var slot = leaf.find(this.order, key);
		if (slot >= 0) {
			var previous = leaf.valueAt(slot);
			if (replaceExisting) {
				leaf.replaceValue(slot, value);
			}
			return previous;
		}
		insertAbsent(depth, leaf, -(slot + 1), key, value);
		return ABSENT;
	}

	private void insertIntoEmpty(Object key, Object value) {
		this.order.requireInsertable(key);
		this.order.resolveInlineKeyFor(key);
		var leaf = this.nodes.newLeaf();
		leaf.insert(this.order, 0, key, value);
		this.root = leaf;
		this.size = 1;
		this.modificationCount++;
	}

	private void insertAbsent(int depth, LeafNode leaf, int slot, Object key, Object value) {
		this.size++;
		this.modificationCount++;
		if (!leaf.isFull()) {
			leaf.insert(this.order, slot, key, value);
			return;
		}
		var highFence = this.pathHighFences[depth];
		var appending = slot == leaf.size() && highFence == Node.NO_FENCE;
		var from = appending ? leaf.size() : leaf.size() / 2;
		var right = leaf.splitOff(this.order, from);
		if (slot < from || (slot == from && !appending)) {
			leaf.insert(this.order, slot, key, value);
		}
		else {
			right.insert(this.order, slot - from, key, value);
		}
		var separator = right.keyAt(0);
		leaf.fitPrefix(this.order, this.pathLowFences[depth], separator);
		right.fitPrefix(this.order, separator, highFence);
		insertIntoParent(depth - 1, separator, right);
	}

	private void insertIntoParent(int level, Object separator, Node rightChild) {
		if (level < 0) {
			var newRoot = this.nodes.newInternal();
			newRoot.becomeRoot(this.order, this.root, separator, rightChild);
			this.root = newRoot;
			return;
		}
		var parent = (InternalNode) this.pathNodes[level];
		var slot = this.pathChildren[level];
		if (!parent.isFull()) {
			parent.insertSeparator(this.order, slot, separator, rightChild);
			return;
		}
		var highFence = this.pathHighFences[level];
		var appending = slot == parent.size() && highFence == Node.NO_FENCE;
		var middle = appending ? parent.size() - 1 : parent.size() / 2;
		var promoted = parent.keyAt(middle);
		var right = parent.splitOff(this.order, middle);
		if (slot <= middle) {
			parent.insertSeparator(this.order, slot, separator, rightChild);
		}
		else {
			right.insertSeparator(this.order, slot - middle - 1, separator, rightChild);
		}
		parent.fitPrefix(this.order, this.pathLowFences[level], promoted);
		right.fitPrefix(this.order, promoted, highFence);
		insertIntoParent(level - 1, promoted, right);
	}

	private void removeAt(int depth, LeafNode leaf, int slot) {
		leaf.remove(slot);
		this.size--;
		this.modificationCount++;
		if (this.size == 0) {
			this.root = null;
			forgetPath();
			return;
		}
		rebalanceUpward(depth);
		while (this.root instanceof InternalNode internal && internal.size() == 0) {
			this.root = internal.childAt(0);
		}
	}

	private void rebalanceUpward(int depth) {
		for (var level = depth; level > 0; level--) {
			var node = this.pathNodes[level];
			if (node.size() >= node.capacity() / 2) {
				return;
			}
			var parent = (InternalNode) this.pathNodes[level - 1];
			if (parent.size() == 0) {
				return;
			}
			var child = this.pathChildren[level - 1];
			var separatorSlot = child < parent.size() ? child : child - 1;
			var merged = parent.mergeOrShareChildren(this.order, separatorSlot, this.pathLowFences[level - 1],
					this.pathHighFences[level - 1]);
			if (!merged) {
				return;
			}
		}
	}

	private int descendRecordingPath(Object key) {
		var node = this.root;
		var lowFence = Node.NO_FENCE;
		var highFence = Node.NO_FENCE;
		var depth = 0;
		while (node instanceof InternalNode internal) {
			var child = internal.route(this.order, key);
			recordPathStep(depth, internal, child, lowFence, highFence);
			lowFence = internal.lowFenceOfChild(child, lowFence);
			highFence = internal.highFenceOfChild(child, highFence);
			node = internal.childAt(child);
			depth++;
		}
		recordPathStep(depth, node, -1, lowFence, highFence);
		return depth;
	}

	private void recordPathStep(int depth, Node node, int child, Object lowFence, Object highFence) {
		this.pathNodes[depth] = node;
		this.pathChildren[depth] = child;
		this.pathLowFences[depth] = lowFence;
		this.pathHighFences[depth] = highFence;
	}

	private void forgetPath() {
		Arrays.fill(this.pathNodes, null);
		Arrays.fill(this.pathLowFences, null);
		Arrays.fill(this.pathHighFences, null);
	}

}
