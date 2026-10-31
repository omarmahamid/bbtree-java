package io.github.omarmahamid.bbtree;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

final class LatchedTree implements OrderedStore {

	private static final Object RESTART = new Object();

	private static final int LANES = 8;

	private static final int UNDERFULL_DIVISOR = 4;

	private static final VarHandle ROOT;

	static {
		try {
			ROOT = MethodHandles.lookup().findVarHandle(LatchedTree.class, "root", Node.class);
		}
		catch (ReflectiveOperationException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}

	private final KeyOrder order;

	private final NodeFactory nodes;

	private final LongAdder entryCount = new LongAdder();

	private volatile Node root;

	LatchedTree(KeyOrder order, NodeConfig config, boolean storesValues) {
		this.order = order;
		this.nodes = new NodeFactory(config, order, storesValues);
	}

	static LatchedTree withEntries(KeyOrder order, Map<?, ?> entries) {
		var tree = new LatchedTree(order, NodeConfig.defaults(), true);
		tree.insertAll(entries);
		return tree;
	}

	static LatchedTree withElements(KeyOrder order, Collection<?> elements) {
		var tree = new LatchedTree(order, NodeConfig.defaults(), false);
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
		return true;
	}

	@Override
	public void requireSearchableKey(Object key) {
		requireKey(key);
	}

	Node root() {
		return this.root;
	}

	LatchedTree emptyCopy() {
		return new LatchedTree(this.order.unresolvedCopy(), this.nodes.config(), this.nodes.storesValues());
	}

	LatchedTree copy() {
		var copy = emptyCopy();
		var entries = SortedEntries.capturedFrom(this, KeyRange.UNBOUNDED, false);
		copy.loadSorted(entries.keys(), entries.values(), entries.count());
		return copy;
	}

	@Override
	public void loadSorted(Object[] sortedKeys, Object[] sortedValues, int count) {
		for (var index = 0; index < count; index++) {
			requireKey(sortedKeys[index]);
			if (sortedValues != null) {
				requireValue(sortedValues[index]);
			}
		}
		this.root = this.nodes.buildSorted(sortedKeys, sortedValues, count);
		this.entryCount.reset();
		this.entryCount.add(count);
	}

	@Override
	public Object get(Object key) {
		requireKey(key);
		for (;;) {
			var outcome = tryOptimisticGet(key);
			if (outcome != RESTART) {
				return outcome;
			}
		}
	}

	@Override
	public Object put(Object key, Object value) {
		requireKey(key);
		requireValue(value);
		var access = latchLeaf(key, LatchIntent.INSERT);
		var leaf = access.leaf();
		try {
			var slot = access.slot();
			if (slot >= 0) {
				var previous = leaf.valueAt(slot);
				leaf.replaceValue(slot, value);
				return previous;
			}
			insertAt(leaf, -(slot + 1), key, value);
			return ABSENT;
		}
		finally {
			leaf.releaseExclusive();
		}
	}

	@Override
	public Object putIfAbsent(Object key, Object value) {
		requireKey(key);
		requireValue(value);
		var access = latchLeaf(key, LatchIntent.INSERT);
		var leaf = access.leaf();
		var slot = access.slot();
		if (slot >= 0) {
			var existing = leaf.valueAt(slot);
			leaf.releaseExclusiveUnchanged();
			return existing;
		}
		try {
			insertAt(leaf, -(slot + 1), key, value);
			return ABSENT;
		}
		finally {
			leaf.releaseExclusive();
		}
	}

	@Override
	public Object replace(Object key, Object value) {
		requireKey(key);
		requireValue(value);
		var access = latchLeaf(key, LatchIntent.WRITE);
		var leaf = access.leaf();
		if (leaf == null) {
			return ABSENT;
		}
		var slot = access.slot();
		if (slot < 0) {
			leaf.releaseExclusiveUnchanged();
			return ABSENT;
		}
		var previous = leaf.valueAt(slot);
		leaf.replaceValue(slot, value);
		leaf.releaseExclusive();
		return previous;
	}

	@Override
	public boolean replace(Object key, Object expected, Object replacement) {
		requireKey(key);
		requireValue(expected);
		requireValue(replacement);
		var access = latchLeaf(key, LatchIntent.WRITE);
		var leaf = access.leaf();
		if (leaf == null) {
			return false;
		}
		var slot = access.slot();
		try {
			if (slot < 0 || !expected.equals(leaf.valueAt(slot))) {
				leaf.releaseExclusiveUnchanged();
				return false;
			}
		}
		catch (RuntimeException failure) {
			leaf.releaseExclusiveUnchanged();
			throw failure;
		}
		leaf.replaceValue(slot, replacement);
		leaf.releaseExclusive();
		return true;
	}

	@Override
	public Object remove(Object key) {
		requireKey(key);
		var access = latchLeaf(key, LatchIntent.REMOVE);
		var leaf = access.leaf();
		if (leaf == null) {
			return ABSENT;
		}
		var slot = access.slot();
		if (slot < 0) {
			leaf.releaseExclusiveUnchanged();
			return ABSENT;
		}
		var previous = leaf.valueAt(slot);
		removeAndRelease(access, slot);
		return previous;
	}

	@Override
	public boolean remove(Object key, Object value) {
		requireKey(key);
		if (value == null) {
			return false;
		}
		var access = latchLeaf(key, LatchIntent.REMOVE);
		var leaf = access.leaf();
		if (leaf == null) {
			return false;
		}
		var slot = access.slot();
		try {
			if (slot < 0 || !value.equals(leaf.valueAt(slot))) {
				leaf.releaseExclusiveUnchanged();
				return false;
			}
		}
		catch (RuntimeException failure) {
			leaf.releaseExclusiveUnchanged();
			throw failure;
		}
		removeAndRelease(access, slot);
		return true;
	}

	@Override
	public long size() {
		return this.entryCount.sum();
	}

	@Override
	public void clear() {
		var cursor = new LeafCursor(this);
		for (var positioned = cursor.seekFirst(); positioned; positioned = cursor.advance()) {
			remove(cursor.key());
		}
	}

	@Override
	public int modificationCount() {
		return 0;
	}

	@Override
	public Object writeThrough(Object key, Object value) {
		requireValue(value);
		var previous = put(key, value);
		return previous == ABSENT ? null : previous;
	}

	@Override
	public void loadLeaf(LeafWindow window, Object key, Routing routing) {
		while (!tryLoadLeaf(window, key, routing)) {
			Thread.onSpinWait();
		}
	}

	Object transform(Object key, boolean mayInsert, BiFunction<Object, Object, Object> successor) {
		requireKey(key);
		var access = latchLeaf(key, mayInsert ? LatchIntent.INSERT : LatchIntent.REMOVE);
		var leaf = access.leaf();
		if (leaf == null) {
			return successor.apply(key, null);
		}
		var slot = access.slot();
		var current = slot >= 0 ? leaf.valueAt(slot) : null;
		Object next;
		try {
			next = successor.apply(key, current);
		}
		catch (RuntimeException | Error failure) {
			leaf.releaseExclusiveUnchanged();
			throw failure;
		}
		if (next == current) {
			leaf.releaseExclusiveUnchanged();
			return current;
		}
		if (next == null) {
			removeAndRelease(access, slot);
			return null;
		}
		try {
			if (slot >= 0) {
				leaf.replaceValue(slot, next);
			}
			else {
				insertAt(leaf, -(slot + 1), key, next);
			}
			return next;
		}
		finally {
			leaf.releaseExclusive();
		}
	}

	<R> R read(Object key, BiFunction<Object, Object, R> reader) {
		requireKey(key);
		var access = latchLeaf(key, LatchIntent.READ);
		var leaf = access.leaf();
		if (leaf == null) {
			return null;
		}
		try {
			var slot = access.slot();
			return slot >= 0 ? reader.apply(leaf.keyAt(slot), leaf.valueAt(slot)) : null;
		}
		finally {
			leaf.releaseShared();
		}
	}

	<R> R update(Object key, BiFunction<Object, Object, R> updater) {
		requireKey(key);
		var access = latchLeaf(key, LatchIntent.WRITE);
		var leaf = access.leaf();
		if (leaf == null) {
			return null;
		}
		try {
			var slot = access.slot();
			return slot >= 0 ? updater.apply(leaf.keyAt(slot), leaf.valueAt(slot)) : null;
		}
		finally {
			leaf.releaseExclusiveUnchanged();
		}
	}

	<R> R getOrInsert(Object key, Object value, BiFunction<Object, Object, R> reader) {
		requireKey(key);
		requireValue(value);
		var access = latchLeaf(key, LatchIntent.INSERT);
		var leaf = access.leaf();
		var slot = access.slot();
		var inserted = slot < 0;
		try {
			if (inserted) {
				slot = -(slot + 1);
				insertAt(leaf, slot, key, value);
			}
			return reader.apply(leaf.keyAt(slot), leaf.valueAt(slot));
		}
		finally {
			if (inserted) {
				leaf.releaseExclusive();
			}
			else {
				leaf.releaseExclusiveUnchanged();
			}
		}
	}

	Map.Entry<Object, Object> removeIf(Object key, BiPredicate<Object, Object> condition) {
		requireKey(key);
		var access = latchLeaf(key, LatchIntent.REMOVE);
		var leaf = access.leaf();
		if (leaf == null) {
			return null;
		}
		var slot = access.slot();
		Map.Entry<Object, Object> removed;
		try {
			if (slot < 0 || !condition.test(leaf.keyAt(slot), leaf.valueAt(slot))) {
				leaf.releaseExclusiveUnchanged();
				return null;
			}
			removed = new AbstractMap.SimpleImmutableEntry<>(leaf.keyAt(slot), leaf.valueAt(slot));
		}
		catch (RuntimeException | Error failure) {
			leaf.releaseExclusiveUnchanged();
			throw failure;
		}
		removeAndRelease(access, slot);
		return removed;
	}

	<R> R scan(KeyRange range, boolean descending, BiFunction<Object, Object, R> visitor) {
		var anchor = descending ? range.high() : range.low();
		var routing = anchor == Node.NO_FENCE ? (descending ? Routing.RIGHTMOST : Routing.LEFTMOST) : Routing.COVERING;
		for (;;) {
			var access = latchLeafShared(anchor, routing);
			if (access == null) {
				continue;
			}
			var leaf = access.leaf();
			if (leaf == null) {
				return null;
			}
			Object nextAnchor;
			try {
				var result = visitLatchedLeaf(leaf, range, descending, visitor);
				if (result != null) {
					return result;
				}
				nextAnchor = descending ? access.lowFence() : access.highFence();
			}
			finally {
				leaf.releaseShared();
			}
			if (nextAnchor == Node.NO_FENCE || leavesRange(range, descending, nextAnchor)) {
				return null;
			}
			anchor = nextAnchor;
			routing = descending ? Routing.BELOW : Routing.COVERING;
			range = descending ? range.withHigh(nextAnchor, false) : range.withLow(nextAnchor, true);
		}
	}

	Object[] getMany(Object[] keys) {
		for (var key : keys) {
			requireKey(key);
		}
		var results = new Object[keys.length];
		for (var start = 0; start < keys.length; start += LANES) {
			getInLockstep(keys, results, start, Math.min(LANES, keys.length - start));
		}
		return results;
	}

	private Object tryOptimisticGet(Object key) {
		var node = this.root;
		if (node == null) {
			return ABSENT;
		}
		var version = node.awaitStableVersion();
		if (node != this.root) {
			return RESTART;
		}
		try {
			while (node instanceof InternalNode internal) {
				var child = internal.childAt(internal.route(this.order, key));
				var childVersion = child.awaitStableVersion();
				if (!internal.hasVersion(version)) {
					return RESTART;
				}
				node = child;
				version = childVersion;
			}
			var leaf = (LeafNode) node;
			var slot = leaf.find(this.order, key);
			var value = slot >= 0 ? leaf.valueAt(slot) : ABSENT;
			return leaf.hasVersion(version) ? value : RESTART;
		}
		catch (RuntimeException failure) {
			if (node.hasVersion(version)) {
				throw failure;
			}
			return RESTART;
		}
	}

	private void getInLockstep(Object[] keys, Object[] results, int start, int laneCount) {
		var laneNodes = new Node[laneCount];
		var laneVersions = new long[laneCount];
		var pending = 0;
		for (var lane = 0; lane < laneCount; lane++) {
			if (restartLane(laneNodes, laneVersions, lane)) {
				pending++;
			}
			else {
				results[start + lane] = ABSENT;
			}
		}
		while (pending > 0) {
			for (var lane = 0; lane < laneCount; lane++) {
				var node = laneNodes[lane];
				if (node == null) {
					continue;
				}
				var outcome = stepLane(keys[start + lane], laneNodes, laneVersions, lane);
				if (outcome == RESTART) {
					if (!restartLane(laneNodes, laneVersions, lane)) {
						results[start + lane] = ABSENT;
						pending--;
					}
				}
				else if (outcome != null) {
					results[start + lane] = outcome;
					laneNodes[lane] = null;
					pending--;
				}
			}
		}
	}

	private boolean restartLane(Node[] laneNodes, long[] laneVersions, int lane) {
		for (;;) {
			var node = this.root;
			if (node == null) {
				laneNodes[lane] = null;
				return false;
			}
			var version = node.awaitStableVersion();
			if (node == this.root) {
				laneNodes[lane] = node;
				laneVersions[lane] = version;
				return true;
			}
		}
	}

	private Object stepLane(Object key, Node[] laneNodes, long[] laneVersions, int lane) {
		var node = laneNodes[lane];
		var version = laneVersions[lane];
		try {
			if (node instanceof InternalNode internal) {
				var child = internal.childAt(internal.route(this.order, key));
				var childVersion = child.awaitStableVersion();
				if (!internal.hasVersion(version)) {
					return RESTART;
				}
				laneNodes[lane] = child;
				laneVersions[lane] = childVersion;
				return null;
			}
			var leaf = (LeafNode) node;
			var slot = leaf.find(this.order, key);
			var value = slot >= 0 ? leaf.valueAt(slot) : ABSENT;
			return leaf.hasVersion(version) ? value : RESTART;
		}
		catch (RuntimeException failure) {
			if (node.hasVersion(version)) {
				throw failure;
			}
			return RESTART;
		}
	}

	private boolean tryLoadLeaf(LeafWindow window, Object key, Routing routing) {
		var node = this.root;
		if (node == null) {
			window.showNothing();
			return true;
		}
		var version = node.awaitStableVersion();
		if (node != this.root) {
			return false;
		}
		var lowFence = Node.NO_FENCE;
		var highFence = Node.NO_FENCE;
		try {
			while (node instanceof InternalNode internal) {
				var child = routing.childFor(internal, this.order, key);
				var next = internal.childAt(child);
				var nextLowFence = internal.lowFenceOfChild(child, lowFence);
				var nextHighFence = internal.highFenceOfChild(child, highFence);
				var nextVersion = next.awaitStableVersion();
				if (!internal.hasVersion(version)) {
					return false;
				}
				lowFence = nextLowFence;
				highFence = nextHighFence;
				node = next;
				version = nextVersion;
			}
			((LeafNode) node).snapshotTo(window, lowFence, highFence);
			return node.hasVersion(version);
		}
		catch (RuntimeException failure) {
			if (node.hasVersion(version)) {
				throw failure;
			}
			return false;
		}
	}

	private LeafAccess latchLeafShared(Object key, Routing routing) {
		var node = this.root;
		if (node == null) {
			return LeafAccess.NONE;
		}
		var version = node.awaitStableVersion();
		if (node != this.root) {
			return null;
		}
		InternalNode parent = null;
		var parentVersion = 0L;
		var lowFence = Node.NO_FENCE;
		var highFence = Node.NO_FENCE;
		try {
			while (node instanceof InternalNode internal) {
				var child = routing.childFor(internal, this.order, key);
				var next = internal.childAt(child);
				var nextLowFence = internal.lowFenceOfChild(child, lowFence);
				var nextHighFence = internal.highFenceOfChild(child, highFence);
				var nextVersion = next.awaitStableVersion();
				if (!internal.hasVersion(version)) {
					return null;
				}
				parent = internal;
				parentVersion = version;
				lowFence = nextLowFence;
				highFence = nextHighFence;
				node = next;
				version = nextVersion;
			}
		}
		catch (RuntimeException failure) {
			if (node.hasVersion(version)) {
				throw failure;
			}
			return null;
		}
		var leaf = (LeafNode) node;
		leaf.awaitShared();
		var stillCovering = parent == null ? this.root == leaf : parent.hasVersion(parentVersion);
		if (!stillCovering) {
			leaf.releaseShared();
			return null;
		}
		return new LeafAccess(leaf, -1, parent, parentVersion, -1, lowFence, highFence, Node.NO_FENCE,
				Node.NO_FENCE);
	}

	private <R> R visitLatchedLeaf(LeafNode leaf, KeyRange range, boolean descending,
			BiFunction<Object, Object, R> visitor) {
		var count = leaf.size();
		for (var step = 0; step < count; step++) {
			var slot = descending ? count - 1 - step : step;
			var key = leaf.keyAt(slot);
			if (descending ? range.tooHigh(this.order, key) : range.tooLow(this.order, key)) {
				continue;
			}
			if (descending ? range.tooLow(this.order, key) : range.tooHigh(this.order, key)) {
				return null;
			}
			var result = visitor.apply(key, leaf.valueAt(slot));
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	private boolean leavesRange(KeyRange range, boolean descending, Object fence) {
		if (descending) {
			return range.hasLow() && this.order.compare(fence, range.low()) <= 0;
		}
		return range.tooHigh(this.order, fence);
	}

	private LeafAccess latchLeaf(Object key, LatchIntent intent) {
		for (;;) {
			var access = tryLatchLeaf(key, intent);
			if (access != null) {
				return access;
			}
		}
	}

	private LeafAccess tryLatchLeaf(Object key, LatchIntent intent) {
		var node = this.root;
		if (node == null) {
			if (intent != LatchIntent.INSERT) {
				return LeafAccess.NONE;
			}
			installFirstLeaf(key);
			return null;
		}
		var version = node.awaitStableVersion();
		if (node != this.root) {
			return null;
		}
		InternalNode parent = null;
		var parentVersion = 0L;
		var child = -1;
		var lowFence = Node.NO_FENCE;
		var highFence = Node.NO_FENCE;
		var parentLowFence = Node.NO_FENCE;
		var parentHighFence = Node.NO_FENCE;
		var structuralChange = StructuralChange.NONE;
		try {
			while (node instanceof InternalNode internal) {
				structuralChange = structuralChangeBefore(internal, parent, intent);
				if (structuralChange != StructuralChange.NONE) {
					break;
				}
				var next = internal.route(this.order, key);
				var childNode = internal.childAt(next);
				var childLowFence = internal.lowFenceOfChild(next, lowFence);
				var childHighFence = internal.highFenceOfChild(next, highFence);
				var childVersion = childNode.awaitStableVersion();
				if (!internal.hasVersion(version)) {
					return null;
				}
				parent = internal;
				parentVersion = version;
				child = next;
				parentLowFence = lowFence;
				parentHighFence = highFence;
				lowFence = childLowFence;
				highFence = childHighFence;
				node = childNode;
				version = childVersion;
			}
		}
		catch (RuntimeException failure) {
			if (node.hasVersion(version)) {
				throw failure;
			}
			return null;
		}
		switch (structuralChange) {
			case SPLIT -> {
				splitInternal((InternalNode) node, version, parent, parentVersion, child, lowFence, highFence, key);
				return null;
			}
			case COLLAPSE_ROOT -> {
				collapseRoot((InternalNode) node, version);
				return null;
			}
			case REBALANCE -> {
				if (rebalanceInternal(parent, parentVersion, child, parentLowFence, parentHighFence,
						(InternalNode) node, version)) {
					return null;
				}
				return tryLatchLeafSkippingRebalance(key, intent);
			}
			case NONE -> {
				return latchFoundLeaf((LeafNode) node, key, intent, parent, parentVersion, child, lowFence,
						highFence, parentLowFence, parentHighFence);
			}
		}
		throw new IllegalStateException("Unknown structural change " + structuralChange);
	}

	private LeafAccess tryLatchLeafSkippingRebalance(Object key, LatchIntent intent) {
		return tryLatchLeaf(key, intent == LatchIntent.REMOVE ? LatchIntent.WRITE : intent);
	}

	private StructuralChange structuralChangeBefore(InternalNode internal, InternalNode parent, LatchIntent intent) {
		if (intent == LatchIntent.INSERT && internal.isFull()) {
			return StructuralChange.SPLIT;
		}
		if (intent != LatchIntent.REMOVE) {
			return StructuralChange.NONE;
		}
		if (parent == null) {
			return internal.boundedSize() == 0 ? StructuralChange.COLLAPSE_ROOT : StructuralChange.NONE;
		}
		return internal.boundedSize() < underfullThreshold(internal) ? StructuralChange.REBALANCE
				: StructuralChange.NONE;
	}

	private LeafAccess latchFoundLeaf(LeafNode leaf, Object key, LatchIntent intent, InternalNode parent,
			long parentVersion, int child, Object lowFence, Object highFence, Object parentLowFence,
			Object parentHighFence) {
		var shared = intent == LatchIntent.READ;
		if (shared) {
			leaf.awaitShared();
		}
		else {
			leaf.awaitExclusive();
		}
		var stillCovering = parent == null ? this.root == leaf : parent.hasVersion(parentVersion);
		if (!stillCovering) {
			releaseUnchanged(leaf, shared);
			return null;
		}
		int slot;
		try {
			slot = leaf.find(this.order, key);
		}
		catch (RuntimeException failure) {
			releaseUnchanged(leaf, shared);
			throw failure;
		}
		if (intent == LatchIntent.INSERT && slot < 0 && leaf.isFull()) {
			splitLeaf(leaf, parent, parentVersion, child, lowFence, highFence, key, -(slot + 1));
			return null;
		}
		return new LeafAccess(leaf, slot, parent, parentVersion, child, lowFence, highFence, parentLowFence,
				parentHighFence);
	}

	private void installFirstLeaf(Object key) {
		this.order.resolveInlineKeyFor(key);
		ROOT.compareAndSet(this, null, this.nodes.newLeaf());
	}

	private void splitLeaf(LeafNode leaf, InternalNode parent, long parentVersion, int child, Object lowFence,
			Object highFence, Object key, int slot) {
		if (parent != null && !parent.tryExclusive(parentVersion)) {
			leaf.releaseExclusiveUnchanged();
			return;
		}
		try {
			var appending = slot == leaf.size() && highFence == Node.NO_FENCE;
			var right = leaf.splitOff(this.order, appending ? leaf.size() : leaf.size() / 2);
			var separator = appending ? key : right.keyAt(0);
			leaf.fitPrefix(this.order, lowFence, separator);
			right.fitPrefix(this.order, separator, highFence);
			if (parent == null) {
				var newRoot = this.nodes.newInternal();
				newRoot.becomeRoot(this.order, leaf, separator, right);
				this.root = newRoot;
			}
			else {
				parent.insertSeparator(this.order, child, separator, right);
			}
		}
		finally {
			if (parent != null) {
				parent.releaseExclusive();
			}
			leaf.releaseExclusive();
		}
	}

	private void splitInternal(InternalNode node, long version, InternalNode parent, long parentVersion, int child,
			Object lowFence, Object highFence, Object key) {
		if (parent != null && !parent.tryExclusive(parentVersion)) {
			return;
		}
		if (!node.tryExclusive(version)) {
			if (parent != null) {
				parent.releaseExclusiveUnchanged();
			}
			return;
		}
		try {
			var size = node.size();
			var appending = highFence == Node.NO_FENCE && node.route(this.order, key) == size;
			var middle = appending ? size - 1 : size / 2;
			var promoted = node.keyAt(middle);
			var right = node.splitOff(this.order, middle);
			node.fitPrefix(this.order, lowFence, promoted);
			right.fitPrefix(this.order, promoted, highFence);
			if (parent == null) {
				var newRoot = this.nodes.newInternal();
				newRoot.becomeRoot(this.order, node, promoted, right);
				this.root = newRoot;
			}
			else {
				parent.insertSeparator(this.order, child, promoted, right);
			}
		}
		finally {
			node.releaseExclusive();
			if (parent != null) {
				parent.releaseExclusive();
			}
		}
	}

	private void collapseRoot(InternalNode node, long version) {
		if (!node.tryExclusive(version)) {
			return;
		}
		try {
			if (this.root == node && node.size() == 0) {
				this.root = node.childAt(0);
			}
		}
		finally {
			node.releaseExclusive();
		}
	}

	private boolean rebalanceInternal(InternalNode parent, long parentVersion, int child, Object parentLowFence,
			Object parentHighFence, InternalNode node, long version) {
		if (!parent.tryExclusive(parentVersion)) {
			return true;
		}
		if (!node.tryExclusive(version)) {
			parent.releaseExclusiveUnchanged();
			return true;
		}
		boolean rebalanced;
		try {
			rebalanced = rebalanceLatchedChild(parent, child, parentLowFence, parentHighFence);
		}
		catch (RuntimeException | Error failure) {
			node.releaseExclusive();
			throw failure;
		}
		if (rebalanced) {
			node.releaseExclusive();
		}
		else {
			node.releaseExclusiveUnchanged();
		}
		return rebalanced;
	}

	private void rebalanceLeaf(LeafAccess access) {
		var parent = access.parent();
		if (parent != null && parent.tryExclusive(access.parentVersion())) {
			rebalanceLatchedChild(parent, access.child(), access.parentLowFence(), access.parentHighFence());
		}
	}

	private boolean rebalanceLatchedChild(InternalNode parent, int child, Object parentLowFence,
			Object parentHighFence) {
		var siblingIndex = child < parent.size() ? child + 1 : child - 1;
		var sibling = siblingIndex >= 0 ? parent.childAt(siblingIndex) : null;
		if (sibling == null || !sibling.tryExclusiveNow()) {
			parent.releaseExclusiveUnchanged();
			return false;
		}
		try {
			parent.mergeOrShareChildren(this.order, Math.min(child, siblingIndex), parentLowFence, parentHighFence);
		}
		finally {
			sibling.releaseExclusive();
			parent.releaseExclusive();
		}
		return true;
	}

	private void insertAt(LeafNode leaf, int slot, Object key, Object value) {
		leaf.insert(this.order, slot, key, value);
		this.entryCount.increment();
	}

	private void removeAndRelease(LeafAccess access, int slot) {
		var leaf = access.leaf();
		try {
			leaf.remove(slot);
			this.entryCount.decrement();
			if (leaf.size() < underfullThreshold(leaf)) {
				rebalanceLeaf(access);
			}
		}
		finally {
			leaf.releaseExclusive();
		}
	}

	private static int underfullThreshold(Node node) {
		return node.capacity() / UNDERFULL_DIVISOR;
	}

	private static void releaseUnchanged(LeafNode leaf, boolean shared) {
		if (shared) {
			leaf.releaseShared();
		}
		else {
			leaf.releaseExclusiveUnchanged();
		}
	}

	private static void requireKey(Object key) {
		Objects.requireNonNull(key, "A concurrent map can not hold a null key");
	}

	private static void requireValue(Object value) {
		Objects.requireNonNull(value, "A concurrent map can not hold a null value");
	}

	private enum LatchIntent {

		READ,

		WRITE,

		INSERT,

		REMOVE

	}

	private enum StructuralChange {

		NONE,

		SPLIT,

		COLLAPSE_ROOT,

		REBALANCE

	}

	private record LeafAccess(LeafNode leaf, int slot, InternalNode parent, long parentVersion, int child,
			Object lowFence, Object highFence, Object parentLowFence, Object parentHighFence) {

		static final LeafAccess NONE = new LeafAccess(null, -1, null, 0, -1, Node.NO_FENCE, Node.NO_FENCE,
				Node.NO_FENCE, Node.NO_FENCE);

	}

}
