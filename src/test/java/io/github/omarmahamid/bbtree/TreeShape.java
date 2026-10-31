package io.github.omarmahamid.bbtree;

final class TreeShape {

	private final KeyOrder order;

	private final boolean sparseNodesAllowed;

	private int leafDepth = -1;

	private long entryCount;

	private TreeShape(KeyOrder order, boolean sparseNodesAllowed) {
		this.order = order;
		this.sparseNodesAllowed = sparseNodesAllowed;
	}

	static long verifiedEntryCount(OrderedStore store) {
		var root = store instanceof Tree tree ? tree.root() : ((LatchedTree) store).root();
		var shape = new TreeShape(store.order(), store.concurrent());
		if (root != null) {
			shape.verify(root, Node.NO_FENCE, Node.NO_FENCE, 0, true);
		}
		return shape.entryCount;
	}

	private void verify(Node node, Object lowFence, Object highFence, int depth, boolean isRoot) {
		require(node.size() <= node.capacity(), "size exceeds capacity");
		requireSlotsPastSizeCleared(node);
		requireKeysSortedWithinFences(node, lowFence, highFence);
		requireWordsMatchKeys(node, lowFence, highFence);
		if (node instanceof LeafNode leaf) {
			require(isRoot || this.sparseNodesAllowed || leaf.size() > 0, "a non-root leaf is empty");
			if (this.leafDepth < 0) {
				this.leafDepth = depth;
			}
			require(this.leafDepth == depth, "leaves sit at different depths");
			this.entryCount += leaf.size();
			return;
		}
		var internal = (InternalNode) node;
		require(isRoot || this.sparseNodesAllowed || internal.size() > 0, "a non-root internal node has no keys");
		for (var child = 0; child <= internal.size(); child++) {
			var childNode = internal.childAt(child);
			require(childNode != null, "a child edge is missing");
			verify(childNode, internal.lowFenceOfChild(child, lowFence), internal.highFenceOfChild(child, highFence),
					depth + 1, false);
		}
	}

	private void requireSlotsPastSizeCleared(Node node) {
		for (var slot = node.size(); slot < node.capacity(); slot++) {
			require(node.keyAt(slot) == null, "a slot past the size still holds a key");
		}
	}

	private void requireKeysSortedWithinFences(Node node, Object lowFence, Object highFence) {
		for (var slot = 0; slot < node.size(); slot++) {
			var key = node.keyAt(slot);
			if (slot > 0) {
				require(this.order.compare(node.keyAt(slot - 1), key) < 0, "keys are not strictly increasing");
			}
			if (lowFence != Node.NO_FENCE) {
				require(this.order.compare(lowFence, key) <= 0, "a key sits below its low fence");
			}
			if (highFence != Node.NO_FENCE) {
				require(this.order.compare(key, highFence) < 0, "a key sits at or above its high fence");
			}
		}
	}

	private void requireWordsMatchKeys(Node node, Object lowFence, Object highFence) {
		if (node.words == null) {
			return;
		}
		require(node.prefixOffset() <= this.order.sharedPrefixLength(lowFence, highFence),
				"the prefix offset exceeds what the fences share");
		for (var slot = 0; slot < node.size(); slot++) {
			var expected = this.order.storedWord(node.keyAt(slot), node.prefixOffset());
			require(node.words[slot] == expected, "an inline word does not match its key");
		}
	}

	private static void require(boolean condition, String violation) {
		if (!condition) {
			throw new AssertionError("Tree invariant violated: " + violation);
		}
	}

}
