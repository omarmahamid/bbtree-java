package io.github.omarmahamid.bbtree;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

abstract sealed class Node permits LeafNode, InternalNode {

	static final Object NO_FENCE = new Object();

	private static final long EXCLUSIVE = 1L;

	private static final int SHARED_SHIFT = 1;

	private static final int SHARED_BITS = 20;

	private static final long SHARED_UNIT = 1L << SHARED_SHIFT;

	private static final long SHARED_MASK = ((1L << SHARED_BITS) - 1) << SHARED_SHIFT;

	private static final long VERSION_UNIT = 1L << (SHARED_SHIFT + SHARED_BITS);

	private static final long VERSION_MASK = -VERSION_UNIT;

	private static final VarHandle LATCH_WORD;

	static {
		try {
			LATCH_WORD = MethodHandles.lookup().findVarHandle(Node.class, "latchWord", long.class);
		}
		catch (ReflectiveOperationException failure) {
			throw new ExceptionInInitializerError(failure);
		}
	}

	private volatile long latchWord;

	final Object[] keys;

	final long[] words;

	int size;

	int prefixOffset;

	Node(int capacity, boolean inlineWords) {
		this.keys = new Object[capacity];
		this.words = inlineWords ? new long[capacity] : null;
	}

	final int size() {
		return this.size;
	}

	final int capacity() {
		return this.keys.length;
	}

	final boolean isFull() {
		return this.size == this.keys.length;
	}

	final Object keyAt(int slot) {
		return this.keys[slot];
	}

	final int prefixOffset() {
		return this.prefixOffset;
	}

	final int boundedSize() {
		return Math.min(this.size, this.keys.length);
	}

	final int find(KeyOrder order, Object key) {
		var count = boundedSize();
		var inlineWords = this.words;
		if (inlineWords == null) {
			return order.binarySearch(this.keys, 0, count, key);
		}
		var offset = this.prefixOffset;
		var target = order.storedWord(key, offset);
		var below = 0;
		var equal = 0;
		for (var slot = 0; slot < count; slot++) {
			var word = inlineWords[slot];
			below += word < target ? 1 : 0;
			equal += word == target ? 1 : 0;
		}
		if (equal == 0) {
			return -(below + 1);
		}
		if (order.windowIdentifiesKey(offset)) {
			return below;
		}
		return order.binarySearch(this.keys, below, Math.min(count, below + equal), key);
	}

	final void fitPrefix(KeyOrder order, Object lowFence, Object highFence) {
		if (this.words == null) {
			return;
		}
		var fitted = order.sharedPrefixLength(lowFence, highFence);
		if (fitted == this.prefixOffset) {
			return;
		}
		this.prefixOffset = fitted;
		encodeWords(order, 0, this.size);
	}

	final void encodeWords(KeyOrder order, int from, int to) {
		if (this.words == null) {
			return;
		}
		for (var slot = from; slot < to; slot++) {
			this.words[slot] = order.storedWord(this.keys[slot], this.prefixOffset);
		}
	}

	final void shiftKeys(int from, int to, int count) {
		System.arraycopy(this.keys, from, this.keys, to, count);
		if (this.words != null) {
			System.arraycopy(this.words, from, this.words, to, count);
		}
	}

	final void copyKeysFrom(KeyOrder order, Node source, int sourceFrom, int destinationFrom, int count) {
		System.arraycopy(source.keys, sourceFrom, this.keys, destinationFrom, count);
		if (this.words == null) {
			return;
		}
		if (source.prefixOffset == this.prefixOffset) {
			System.arraycopy(source.words, sourceFrom, this.words, destinationFrom, count);
		}
		else {
			encodeWords(order, destinationFrom, destinationFrom + count);
		}
	}

	final void writeKey(KeyOrder order, int slot, Object key) {
		this.keys[slot] = key;
		if (this.words != null) {
			this.words[slot] = order.storedWord(key, this.prefixOffset);
		}
	}

	final void clearKeys(int from, int to) {
		Arrays.fill(this.keys, from, to, null);
	}

	final void inheritPrefixOffset(Node source) {
		this.prefixOffset = source.prefixOffset;
	}

	final long awaitStableVersion() {
		var word = this.latchWord;
		if ((word & EXCLUSIVE) == 0) {
			return word & VERSION_MASK;
		}
		var backoff = new Backoff();
		while (((word = this.latchWord) & EXCLUSIVE) != 0) {
			backoff.pause();
		}
		return word & VERSION_MASK;
	}

	final boolean hasVersion(long version) {
		VarHandle.acquireFence();
		return (this.latchWord & (VERSION_MASK | EXCLUSIVE)) == version;
	}

	final boolean tryExclusive(long version) {
		return LATCH_WORD.compareAndSet(this, version, version | EXCLUSIVE);
	}

	final boolean tryExclusiveNow() {
		var word = this.latchWord;
		return (word & (EXCLUSIVE | SHARED_MASK)) == 0 && LATCH_WORD.compareAndSet(this, word, word | EXCLUSIVE);
	}

	final void awaitExclusive() {
		if (tryExclusiveNow()) {
			return;
		}
		var backoff = new Backoff();
		while (!tryExclusiveNow()) {
			backoff.pause();
		}
	}

	final void releaseExclusive() {
		this.latchWord = (this.latchWord & VERSION_MASK) + VERSION_UNIT;
	}

	final void releaseExclusiveUnchanged() {
		this.latchWord = this.latchWord & VERSION_MASK;
	}

	final void awaitShared() {
		Backoff backoff = null;
		for (;;) {
			var word = this.latchWord;
			var sharable = (word & EXCLUSIVE) == 0 && (word & SHARED_MASK) != SHARED_MASK;
			if (sharable && LATCH_WORD.compareAndSet(this, word, word + SHARED_UNIT)) {
				return;
			}
			if (backoff == null) {
				backoff = new Backoff();
			}
			backoff.pause();
		}
	}

	final void releaseShared() {
		LATCH_WORD.getAndAdd(this, -SHARED_UNIT);
	}

}
