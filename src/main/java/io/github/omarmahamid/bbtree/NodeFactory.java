package io.github.omarmahamid.bbtree;

final class NodeFactory {

	private final NodeConfig config;

	private final KeyOrder order;

	private final boolean storesValues;

	NodeFactory(NodeConfig config, KeyOrder order, boolean storesValues) {
		this.config = config;
		this.order = order;
		this.storesValues = storesValues;
	}

	NodeConfig config() {
		return this.config;
	}

	boolean storesValues() {
		return this.storesValues;
	}

	LeafNode newLeaf() {
		return new LeafNode(this.config.leafCapacity(), this.order.hasInlineKeys(), this.storesValues);
	}

	InternalNode newInternal() {
		return new InternalNode(this.config.internalCapacity(), this.order.hasInlineKeys());
	}

	Node buildSorted(Object[] sortedKeys, Object[] sortedValues, int count) {
		if (count == 0) {
			return null;
		}
		this.order.resolveInlineKeyFor(sortedKeys[0]);
		var leafCount = Math.ceilDiv(count, this.config.leafCapacity());
		var level = new Node[leafCount];
		var firstKeys = new Object[leafCount];
		for (var index = 0; index < leafCount; index++) {
			var from = evenSplitPoint(count, leafCount, index);
			var to = evenSplitPoint(count, leafCount, index + 1);
			var leaf = newLeaf();
			leaf.appendSorted(this.order, sortedKeys, sortedValues, from, to - from);
			level[index] = leaf;
			firstKeys[index] = sortedKeys[from];
		}
		var levelSize = leafCount;
		while (levelSize > 1) {
			var parentCount = Math.ceilDiv(levelSize, this.config.internalCapacity() + 1);
			var parents = new Node[parentCount];
			var parentFirstKeys = new Object[parentCount];
			for (var index = 0; index < parentCount; index++) {
				var from = evenSplitPoint(levelSize, parentCount, index);
				var to = evenSplitPoint(levelSize, parentCount, index + 1);
				var parent = newInternal();
				parent.adoptFirstChild(level[from]);
				for (var child = from + 1; child < to; child++) {
					parent.appendChild(this.order, firstKeys[child], level[child]);
				}
				parents[index] = parent;
				parentFirstKeys[index] = firstKeys[from];
			}
			level = parents;
			firstKeys = parentFirstKeys;
			levelSize = parentCount;
		}
		var root = level[0];
		fitPrefixes(root, Node.NO_FENCE, Node.NO_FENCE);
		return root;
	}

	private void fitPrefixes(Node node, Object lowFence, Object highFence) {
		node.fitPrefix(this.order, lowFence, highFence);
		if (node instanceof InternalNode internal) {
			for (var child = 0; child <= internal.size(); child++) {
				fitPrefixes(internal.childAt(child), internal.lowFenceOfChild(child, lowFence),
						internal.highFenceOfChild(child, highFence));
			}
		}
	}

	private static int evenSplitPoint(int total, int parts, int index) {
		return (int) ((long) total * index / parts);
	}

}
