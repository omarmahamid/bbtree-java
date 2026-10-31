package io.github.omarmahamid.bbtree;

import java.util.concurrent.ConcurrentNavigableMap;

class ConcurrentNavigableRangeView<K, V> extends NavigableRangeView<K, V> implements ConcurrentNavigableMap<K, V> {

	ConcurrentNavigableRangeView(OrderedStore store, KeyRange range, boolean descending) {
		super(store, range, descending);
	}

	@Override
	NavigableRangeView<K, V> newView(KeyRange viewRange, boolean viewDescending) {
		return new ConcurrentNavigableRangeView<>(store(), viewRange, viewDescending);
	}

	@Override
	public ConcurrentNavigableMap<K, V> descendingMap() {
		return (ConcurrentNavigableMap<K, V>) super.descendingMap();
	}

	@Override
	public ConcurrentNavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return (ConcurrentNavigableMap<K, V>) super.subMap(fromKey, fromInclusive, toKey, toInclusive);
	}

	@Override
	public ConcurrentNavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		return (ConcurrentNavigableMap<K, V>) super.headMap(toKey, inclusive);
	}

	@Override
	public ConcurrentNavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return (ConcurrentNavigableMap<K, V>) super.tailMap(fromKey, inclusive);
	}

	@Override
	public ConcurrentNavigableMap<K, V> subMap(K fromKey, K toKey) {
		return (ConcurrentNavigableMap<K, V>) super.subMap(fromKey, toKey);
	}

	@Override
	public ConcurrentNavigableMap<K, V> headMap(K toKey) {
		return (ConcurrentNavigableMap<K, V>) super.headMap(toKey);
	}

	@Override
	public ConcurrentNavigableMap<K, V> tailMap(K fromKey) {
		return (ConcurrentNavigableMap<K, V>) super.tailMap(fromKey);
	}

}
