package io.github.omarmahamid.bbtree;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;

record SortedEntries(Object[] keys, Object[] values, int count) {

	private static final int INITIAL_CAPACITY = 16;

	static SortedEntries capturedFrom(LeafSource source, KeyRange range, boolean descending) {
		var order = source.order();
		var keys = new Object[INITIAL_CAPACITY];
		var values = new Object[INITIAL_CAPACITY];
		var count = 0;
		var cursor = new LeafCursor(source);
		var positioned = cursor.seekRangeStart(range, descending);
		while (positioned) {
			var key = cursor.key();
			if (descending ? range.tooLow(order, key) : range.tooHigh(order, key)) {
				break;
			}
			if (count == keys.length) {
				keys = Arrays.copyOf(keys, count * 2);
				values = Arrays.copyOf(values, count * 2);
			}
			keys[count] = key;
			values[count] = cursor.value();
			count++;
			positioned = descending ? cursor.retreat() : cursor.advance();
		}
		return new SortedEntries(Arrays.copyOf(keys, count), Arrays.copyOf(values, count), count);
	}

	static SortedEntries presortedIn(Object source, Comparator<?> comparator) {
		if (source instanceof SortedMap<?, ?> map && !map.isEmpty() && Objects.equals(map.comparator(), comparator)) {
			return copiedFrom(map);
		}
		if (source instanceof SortedSet<?> set && !set.isEmpty() && Objects.equals(set.comparator(), comparator)) {
			var keys = set.toArray();
			return new SortedEntries(keys, null, keys.length);
		}
		return null;
	}

	private static SortedEntries copiedFrom(SortedMap<?, ?> map) {
		var keys = new Object[map.size()];
		var values = new Object[keys.length];
		var count = 0;
		for (var entry : map.entrySet()) {
			if (count == keys.length) {
				keys = Arrays.copyOf(keys, count * 2 + 1);
				values = Arrays.copyOf(values, keys.length);
			}
			keys[count] = entry.getKey();
			values[count] = entry.getValue();
			count++;
		}
		return new SortedEntries(keys, values, count);
	}

}
