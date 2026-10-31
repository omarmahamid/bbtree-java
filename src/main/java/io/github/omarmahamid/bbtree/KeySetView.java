package io.github.omarmahamid.bbtree;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.SortedSet;

class KeySetView<E> extends AbstractSet<E> implements NavigableSet<E>, Serializable {

	private NavigableRangeView<E, Object> map;

	private final Object addedValue;

	@SuppressWarnings("unchecked")
	KeySetView(NavigableRangeView<E, ?> map, Object addedValue) {
		this.map = (NavigableRangeView<E, Object>) map;
		this.addedValue = addedValue;
	}

	final NavigableRangeView<E, Object> map() {
		return this.map;
	}

	void adoptMap(NavigableRangeView<E, Object> replacement) {
		this.map = replacement;
	}

	@Override
	public Iterator<E> iterator() {
		return this.map.keyIterator();
	}

	@Override
	public Iterator<E> descendingIterator() {
		return this.map.descendingKeyIterator();
	}

	@Override
	public int size() {
		return this.map.size();
	}

	@Override
	public boolean isEmpty() {
		return this.map.isEmpty();
	}

	@Override
	public boolean contains(Object candidate) {
		return this.map.containsKey(candidate);
	}

	@Override
	public boolean add(E element) {
		if (this.addedValue == null) {
			throw new UnsupportedOperationException("A key set view of a map does not support add");
		}
		return this.map.insertIfAbsent(element, this.addedValue);
	}

	@Override
	public boolean remove(Object candidate) {
		return this.map.removeKey(candidate);
	}

	@Override
	public void clear() {
		this.map.clear();
	}

	@Override
	public Comparator<? super E> comparator() {
		return this.map.comparator();
	}

	@Override
	public E first() {
		return this.map.firstKey();
	}

	@Override
	public E last() {
		return this.map.lastKey();
	}

	@Override
	public E lower(E element) {
		return this.map.lowerKey(element);
	}

	@Override
	public E floor(E element) {
		return this.map.floorKey(element);
	}

	@Override
	public E ceiling(E element) {
		return this.map.ceilingKey(element);
	}

	@Override
	public E higher(E element) {
		return this.map.higherKey(element);
	}

	@Override
	public E pollFirst() {
		var entry = this.map.pollFirstEntry();
		return entry == null ? null : entry.getKey();
	}

	@Override
	public E pollLast() {
		var entry = this.map.pollLastEntry();
		return entry == null ? null : entry.getKey();
	}

	@Override
	public NavigableSet<E> descendingSet() {
		return viewOf(this.map.descendingMap());
	}

	@Override
	public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return viewOf(this.map.subMap(fromElement, fromInclusive, toElement, toInclusive));
	}

	@Override
	public NavigableSet<E> headSet(E toElement, boolean inclusive) {
		return viewOf(this.map.headMap(toElement, inclusive));
	}

	@Override
	public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		return viewOf(this.map.tailMap(fromElement, inclusive));
	}

	@Override
	public SortedSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	@Override
	public SortedSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	@Override
	public SortedSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
	}

	@Serial
	protected final Object writeReplace() {
		var entries = this.map.capturedEntries();
		var kind = this.map.store().concurrent() ? SortedContents.Kind.CONCURRENT_SET : SortedContents.Kind.SET;
		var inlineKey = this.map.coversWholeStore() ? this.map.store().order().declaredInlineKey() : null;
		return new SortedContents(kind, comparator(), inlineKey, this.map.store().nodeConfig(), entries.keys(), null);
	}

	@Serial
	private void readObject(ObjectInputStream stream) throws InvalidObjectException {
		throw new InvalidObjectException("A serialization proxy is required");
	}

	private NavigableSet<E> viewOf(NavigableMap<E, Object> rangedMap) {
		return new KeySetView<>((NavigableRangeView<E, Object>) rangedMap, this.addedValue);
	}

}
