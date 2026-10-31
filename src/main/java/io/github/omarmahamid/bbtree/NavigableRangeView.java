package io.github.omarmahamid.bbtree;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

class NavigableRangeView<K, V> extends AbstractMap<K, V> implements NavigableMap<K, V>, Serializable {

	private OrderedStore store;

	private final KeyRange range;

	private final boolean descending;

	private transient EntrySetView entrySetView;

	private transient KeySetView<K> keySetView;

	NavigableRangeView(OrderedStore store, KeyRange range, boolean descending) {
		this.store = store;
		this.range = range;
		this.descending = descending;
	}

	final OrderedStore store() {
		return this.store;
	}

	void adoptStore(OrderedStore replacement) {
		this.store = replacement;
		this.entrySetView = null;
		this.keySetView = null;
	}

	NavigableRangeView<K, V> newView(KeyRange viewRange, boolean viewDescending) {
		return new NavigableRangeView<>(this.store, viewRange, viewDescending);
	}

	boolean coversWholeStore() {
		return this.range.isUnbounded() && !this.descending;
	}

	SortedEntries capturedEntries() {
		return SortedEntries.capturedFrom(this.store, this.range, this.descending);
	}

	Iterator<K> keyIterator() {
		return new CursorIterator<>(this.store, this.range, this.descending, IterationTarget.KEY);
	}

	Iterator<K> descendingKeyIterator() {
		return new CursorIterator<>(this.store, this.range, !this.descending, IterationTarget.KEY);
	}

	boolean insertIfAbsent(Object key, Object value) {
		requireInRange(key);
		return this.store.putIfAbsent(key, value) == OrderedStore.ABSENT;
	}

	boolean removeKey(Object key) {
		return inRange(key) && this.store.remove(key) != OrderedStore.ABSENT;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Comparator<? super K> comparator() {
		var ascending = (Comparator<? super K>) this.store.order().comparator();
		if (!this.descending) {
			return ascending;
		}
		return ascending == null ? (Comparator<? super K>) (Comparator<?>) Collections.reverseOrder()
				: Collections.reverseOrder(ascending);
	}

	@Override
	public int size() {
		if (this.range.isUnbounded()) {
			return (int) Math.min(this.store.size(), Integer.MAX_VALUE);
		}
		var count = 0L;
		for (var iterator = keyIterator(); iterator.hasNext(); iterator.next()) {
			count++;
		}
		return (int) Math.min(count, Integer.MAX_VALUE);
	}

	@Override
	public boolean isEmpty() {
		if (this.range.isUnbounded() && !this.store.concurrent()) {
			return this.store.size() == 0;
		}
		return lowestCursor() == null;
	}

	@Override
	public boolean containsKey(Object key) {
		return inRange(key) && this.store.get(key) != OrderedStore.ABSENT;
	}

	@Override
	public V get(Object key) {
		return inRange(key) ? valueOrNull(this.store.get(key)) : null;
	}

	@Override
	public V put(K key, V value) {
		requireInRange(key);
		return valueOrNull(this.store.put(key, value));
	}

	@Override
	public V remove(Object key) {
		return inRange(key) ? valueOrNull(this.store.remove(key)) : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V putIfAbsent(K key, V value) {
		requireInRange(key);
		var existing = this.store.putIfAbsent(key, value);
		if (existing == OrderedStore.ABSENT) {
			return null;
		}
		if (existing == null) {
			this.store.put(key, value);
			return null;
		}
		return (V) existing;
	}

	@Override
	public V replace(K key, V value) {
		return inRange(key) ? valueOrNull(this.store.replace(key, value)) : null;
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue) {
		return inRange(key) && this.store.replace(key, oldValue, newValue);
	}

	@Override
	public boolean remove(Object key, Object value) {
		return inRange(key) && this.store.remove(key, value);
	}

	@Override
	public void clear() {
		if (this.range.isUnbounded()) {
			this.store.clear();
			return;
		}
		for (var iterator = keyIterator(); iterator.hasNext();) {
			iterator.next();
			iterator.remove();
		}
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		if (this.entrySetView == null) {
			this.entrySetView = new EntrySetView();
		}
		return this.entrySetView;
	}

	@Override
	public NavigableSet<K> keySet() {
		return navigableKeySet();
	}

	@Override
	public NavigableSet<K> navigableKeySet() {
		if (this.keySetView == null) {
			this.keySetView = new KeySetView<>(this, null);
		}
		return this.keySetView;
	}

	@Override
	public NavigableSet<K> descendingKeySet() {
		return descendingMap().navigableKeySet();
	}

	@Override
	public Map.Entry<K, V> firstEntry() {
		return exported(this.descending ? highestCursor() : lowestCursor());
	}

	@Override
	public Map.Entry<K, V> lastEntry() {
		return exported(this.descending ? lowestCursor() : highestCursor());
	}

	@Override
	public K firstKey() {
		return keyOrThrow(this.descending ? highestCursor() : lowestCursor());
	}

	@Override
	public K lastKey() {
		return keyOrThrow(this.descending ? lowestCursor() : highestCursor());
	}

	@Override
	public Map.Entry<K, V> pollFirstEntry() {
		return pollEdge(!this.descending);
	}

	@Override
	public Map.Entry<K, V> pollLastEntry() {
		return pollEdge(this.descending);
	}

	@Override
	public Map.Entry<K, V> lowerEntry(K key) {
		return exported(relativeCursor(key, directed(Relation.LOWER)));
	}

	@Override
	public K lowerKey(K key) {
		return keyOrNull(relativeCursor(key, directed(Relation.LOWER)));
	}

	@Override
	public Map.Entry<K, V> floorEntry(K key) {
		return exported(relativeCursor(key, directed(Relation.FLOOR)));
	}

	@Override
	public K floorKey(K key) {
		return keyOrNull(relativeCursor(key, directed(Relation.FLOOR)));
	}

	@Override
	public Map.Entry<K, V> ceilingEntry(K key) {
		return exported(relativeCursor(key, directed(Relation.CEILING)));
	}

	@Override
	public K ceilingKey(K key) {
		return keyOrNull(relativeCursor(key, directed(Relation.CEILING)));
	}

	@Override
	public Map.Entry<K, V> higherEntry(K key) {
		return exported(relativeCursor(key, directed(Relation.HIGHER)));
	}

	@Override
	public K higherKey(K key) {
		return keyOrNull(relativeCursor(key, directed(Relation.HIGHER)));
	}

	@Override
	public NavigableMap<K, V> descendingMap() {
		return newView(this.range, !this.descending);
	}

	@Override
	public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return this.descending ? narrowedTo(toKey, toInclusive, fromKey, fromInclusive)
				: narrowedTo(fromKey, fromInclusive, toKey, toInclusive);
	}

	@Override
	public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		return this.descending ? narrowedTo(toKey, inclusive, Node.NO_FENCE, false)
				: narrowedTo(Node.NO_FENCE, false, toKey, inclusive);
	}

	@Override
	public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return this.descending ? narrowedTo(Node.NO_FENCE, false, fromKey, inclusive)
				: narrowedTo(fromKey, inclusive, Node.NO_FENCE, false);
	}

	@Override
	public NavigableMap<K, V> subMap(K fromKey, K toKey) {
		return subMap(fromKey, true, toKey, false);
	}

	@Override
	public NavigableMap<K, V> headMap(K toKey) {
		return headMap(toKey, false);
	}

	@Override
	public NavigableMap<K, V> tailMap(K fromKey) {
		return tailMap(fromKey, true);
	}

	@Serial
	protected final Object writeReplace() {
		var entries = capturedEntries();
		var kind = this.store.concurrent() ? SortedContents.Kind.CONCURRENT_MAP : SortedContents.Kind.MAP;
		var inlineKey = coversWholeStore() ? this.store.order().declaredInlineKey() : null;
		return new SortedContents(kind, comparator(), inlineKey, this.store.nodeConfig(), entries.keys(),
				entries.values());
	}

	@Serial
	private void readObject(ObjectInputStream stream) throws InvalidObjectException {
		throw new InvalidObjectException("A serialization proxy is required");
	}

	private boolean inRange(Object key) {
		return this.range.isUnbounded() || this.range.contains(this.store.order(), key);
	}

	private void requireInRange(Object key) {
		if (!inRange(key)) {
			throw new IllegalArgumentException("key out of range");
		}
	}

	private Relation directed(Relation relation) {
		return this.descending ? relation.mirrored() : relation;
	}

	private LeafCursor lowestCursor() {
		var cursor = new LeafCursor(this.store);
		if (!cursor.seekRangeStart(this.range, false)) {
			return null;
		}
		return this.range.tooHigh(this.store.order(), cursor.key()) ? null : cursor;
	}

	private LeafCursor highestCursor() {
		var cursor = new LeafCursor(this.store);
		if (!cursor.seekRangeStart(this.range, true)) {
			return null;
		}
		return this.range.tooLow(this.store.order(), cursor.key()) ? null : cursor;
	}

	private LeafCursor relativeCursor(Object key, Relation relation) {
		this.store.requireSearchableKey(key);
		var order = this.store.order();
		if (this.range.tooLow(order, key)) {
			return relation.ascending() ? lowestCursor() : null;
		}
		if (this.range.tooHigh(order, key)) {
			return relation.ascending() ? null : highestCursor();
		}
		var cursor = new LeafCursor(this.store);
		if (!cursor.seek(key, relation)) {
			return null;
		}
		var found = cursor.key();
		var outside = relation.ascending() ? this.range.tooHigh(order, found) : this.range.tooLow(order, found);
		return outside ? null : cursor;
	}

	@SuppressWarnings("unchecked")
	private Map.Entry<K, V> pollEdge(boolean lowestKey) {
		for (;;) {
			var cursor = lowestKey ? lowestCursor() : highestCursor();
			if (cursor == null) {
				return null;
			}
			var key = cursor.key();
			var removed = this.store.remove(key);
			if (removed != OrderedStore.ABSENT) {
				return new AbstractMap.SimpleImmutableEntry<>((K) key, (V) removed);
			}
		}
	}

	private NavigableRangeView<K, V> narrowedTo(Object low, boolean lowInclusive, Object high, boolean highInclusive) {
		var order = this.store.order();
		var hasLow = low != Node.NO_FENCE;
		var hasHigh = high != Node.NO_FENCE;
		if (hasLow) {
			this.store.requireSearchableKey(low);
		}
		if (hasHigh) {
			this.store.requireSearchableKey(high);
		}
		if (hasLow && hasHigh) {
			if (order.compare(low, high) > 0) {
				throw new IllegalArgumentException("fromKey > toKey");
			}
		}
		else if (hasLow) {
			order.compare(low, low);
		}
		else if (hasHigh) {
			order.compare(high, high);
		}
		if (hasLow && !this.range.admits(order, low, lowInclusive)) {
			throw new IllegalArgumentException(this.descending ? "toKey out of range" : "fromKey out of range");
		}
		if (hasHigh && !this.range.admits(order, high, highInclusive)) {
			throw new IllegalArgumentException(this.descending ? "fromKey out of range" : "toKey out of range");
		}
		var narrowed = new KeyRange(hasLow ? low : this.range.low(), hasLow ? lowInclusive : this.range.lowInclusive(),
				hasHigh ? high : this.range.high(), hasHigh ? highInclusive : this.range.highInclusive());
		return newView(narrowed, this.descending);
	}

	@SuppressWarnings("unchecked")
	private Map.Entry<K, V> exported(LeafCursor cursor) {
		return cursor == null ? null : new AbstractMap.SimpleImmutableEntry<>((K) cursor.key(), (V) cursor.value());
	}

	@SuppressWarnings("unchecked")
	private K keyOrNull(LeafCursor cursor) {
		return cursor == null ? null : (K) cursor.key();
	}

	private K keyOrThrow(LeafCursor cursor) {
		if (cursor == null) {
			throw new NoSuchElementException();
		}
		return keyOrNull(cursor);
	}

	@SuppressWarnings("unchecked")
	private V valueOrNull(Object storedValue) {
		return storedValue == OrderedStore.ABSENT ? null : (V) storedValue;
	}

	private final class EntrySetView extends AbstractSet<Map.Entry<K, V>> {

		@Override
		public Iterator<Map.Entry<K, V>> iterator() {
			return new CursorIterator<>(NavigableRangeView.this.store, NavigableRangeView.this.range,
					NavigableRangeView.this.descending, IterationTarget.ENTRY);
		}

		@Override
		public int size() {
			return NavigableRangeView.this.size();
		}

		@Override
		public boolean isEmpty() {
			return NavigableRangeView.this.isEmpty();
		}

		@Override
		public boolean contains(Object candidate) {
			if (!(candidate instanceof Map.Entry<?, ?> entry) || !inRange(entry.getKey())) {
				return false;
			}
			var value = NavigableRangeView.this.store.get(entry.getKey());
			return value != OrderedStore.ABSENT && Objects.equals(value, entry.getValue());
		}

		@Override
		public boolean remove(Object candidate) {
			return candidate instanceof Map.Entry<?, ?> entry && inRange(entry.getKey())
					&& NavigableRangeView.this.store.remove(entry.getKey(), entry.getValue());
		}

		@Override
		public void clear() {
			NavigableRangeView.this.clear();
		}

	}

}
