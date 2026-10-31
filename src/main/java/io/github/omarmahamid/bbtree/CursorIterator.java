package io.github.omarmahamid.bbtree;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

final class CursorIterator<T> implements Iterator<T> {

	private static final Object NOTHING_RETURNED = new Object();

	private final OrderedStore store;

	private final KeyRange range;

	private final boolean descending;

	private final IterationTarget target;

	private final LeafCursor cursor;

	private boolean positioned;

	private Object lastKey = NOTHING_RETURNED;

	private int expectedModificationCount;

	CursorIterator(OrderedStore store, KeyRange range, boolean descending, IterationTarget target) {
		this.store = store;
		this.range = range;
		this.descending = descending;
		this.target = target;
		this.cursor = new LeafCursor(store);
		this.expectedModificationCount = store.modificationCount();
		this.positioned = this.cursor.seekRangeStart(range, descending) && cursorWithinFarBound();
	}

	@Override
	public boolean hasNext() {
		return this.positioned;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T next() {
		if (!this.positioned) {
			throw new NoSuchElementException();
		}
		requireUnmodifiedStore();
		var key = this.cursor.key();
		var value = this.cursor.value();
		this.lastKey = key;
		this.positioned = (this.descending ? this.cursor.retreat() : this.cursor.advance()) && cursorWithinFarBound();
		return (T) this.target.project(this.store, key, value);
	}

	@Override
	public void remove() {
		if (this.lastKey == NOTHING_RETURNED) {
			throw new IllegalStateException("next() has not been called since the last remove()");
		}
		requireUnmodifiedStore();
		var upcoming = this.positioned ? this.cursor.key() : null;
		this.store.remove(this.lastKey);
		this.lastKey = NOTHING_RETURNED;
		this.expectedModificationCount = this.store.modificationCount();
		if (this.positioned) {
			var relation = this.descending ? Relation.FLOOR : Relation.CEILING;
			this.positioned = this.cursor.seek(upcoming, relation) && cursorWithinFarBound();
		}
	}

	private boolean cursorWithinFarBound() {
		var key = this.cursor.key();
		var order = this.store.order();
		return this.descending ? !this.range.tooLow(order, key) : !this.range.tooHigh(order, key);
	}

	private void requireUnmodifiedStore() {
		if (this.store.modificationCount() != this.expectedModificationCount) {
			throw new ConcurrentModificationException();
		}
	}

}
