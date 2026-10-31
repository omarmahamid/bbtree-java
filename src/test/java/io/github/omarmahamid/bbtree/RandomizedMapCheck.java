package io.github.omarmahamid.bbtree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Random;

final class RandomizedMapCheck {

	private static final int FULL_COMPARISON_INTERVAL = 997;

	private final NavigableMap<Object, Integer> actual;

	private final NavigableMap<Object, Integer> expected;

	private final KeyKind keyKind;

	private final Random random;

	private final Runnable invariantCheck;

	private int step;

	RandomizedMapCheck(NavigableMap<Object, Integer> actual, NavigableMap<Object, Integer> expected, KeyKind keyKind,
			long seed, Runnable invariantCheck) {
		this.actual = actual;
		this.expected = expected;
		this.keyKind = keyKind;
		this.random = new Random(seed);
		this.invariantCheck = invariantCheck;
	}

	void run(int operations) {
		for (this.step = 0; this.step < operations; this.step++) {
			performRandomOperation();
			if (this.step % FULL_COMPARISON_INTERVAL == 0) {
				requireSameContents();
			}
		}
		requireSameContents();
		drainHalfThroughIterator();
		requireSameContents();
	}

	private void performRandomOperation() {
		var key = this.keyKind.nextKey(this.random);
		var value = this.random.nextInt(1_000);
		var choice = this.random.nextInt(100);
		if (choice < 30) {
			same("put", this.actual.put(key, value), this.expected.put(key, value));
		}
		else if (choice < 46) {
			same("remove", this.actual.remove(key), this.expected.remove(key));
		}
		else if (choice < 54) {
			same("get", this.actual.get(key), this.expected.get(key));
			same("containsKey", this.actual.containsKey(key), this.expected.containsKey(key));
		}
		else if (choice < 58) {
			same("putIfAbsent", this.actual.putIfAbsent(key, value), this.expected.putIfAbsent(key, value));
		}
		else if (choice < 61) {
			same("replace", this.actual.replace(key, value), this.expected.replace(key, value));
		}
		else if (choice < 63) {
			var current = Objects.requireNonNullElse(this.expected.get(key), value);
			same("replaceIfEqual", this.actual.replace(key, current, value), this.expected.replace(key, current, value));
		}
		else if (choice < 65) {
			var current = this.random.nextBoolean() ? this.expected.get(key) : Integer.valueOf(value);
			same("removeIfEqual", this.actual.remove(key, current), this.expected.remove(key, current));
		}
		else if (choice < 75) {
			compareNavigation(key);
		}
		else if (choice < 77) {
			same("firstEntry", this.actual.firstEntry(), this.expected.firstEntry());
			same("lastEntry", this.actual.lastEntry(), this.expected.lastEntry());
		}
		else if (choice < 78) {
			if (this.random.nextBoolean()) {
				same("pollFirstEntry", this.actual.pollFirstEntry(), this.expected.pollFirstEntry());
			}
			else {
				same("pollLastEntry", this.actual.pollLastEntry(), this.expected.pollLastEntry());
			}
		}
		else if (choice < 84) {
			compareComputations(key, value);
		}
		else if (choice < 88) {
			compareRangeView(key, this.keyKind.nextKey(this.random));
		}
		else if (choice < 89) {
			removeThroughRangeIterator(key, this.keyKind.nextKey(this.random));
		}
		else {
			same("size", this.actual.size(), this.expected.size());
		}
	}

	private void compareNavigation(Object key) {
		same("ceilingEntry", this.actual.ceilingEntry(key), this.expected.ceilingEntry(key));
		same("floorEntry", this.actual.floorEntry(key), this.expected.floorEntry(key));
		same("higherEntry", this.actual.higherEntry(key), this.expected.higherEntry(key));
		same("lowerEntry", this.actual.lowerEntry(key), this.expected.lowerEntry(key));
		same("ceilingKey", this.actual.ceilingKey(key), this.expected.ceilingKey(key));
		same("lowerKey", this.actual.lowerKey(key), this.expected.lowerKey(key));
	}

	private void compareComputations(Object key, Integer value) {
		switch (this.random.nextInt(4)) {
			case 0 -> same("merge", this.actual.merge(key, value, RandomizedMapCheck::sumOrRemove),
					this.expected.merge(key, value, RandomizedMapCheck::sumOrRemove));
			case 1 -> same("compute", this.actual.compute(key, (k, v) -> v == null ? value : v % 3 == 0 ? null : v + 1),
					this.expected.compute(key, (k, v) -> v == null ? value : v % 3 == 0 ? null : v + 1));
			case 2 -> same("computeIfAbsent", this.actual.computeIfAbsent(key, k -> value),
					this.expected.computeIfAbsent(key, k -> value));
			default -> same("computeIfPresent", this.actual.computeIfPresent(key, (k, v) -> v % 2 == 0 ? null : v * 2),
					this.expected.computeIfPresent(key, (k, v) -> v % 2 == 0 ? null : v * 2));
		}
	}

	private void compareRangeView(Object first, Object second) {
		var comparator = comparatorOf(this.expected);
		var low = comparator.compare(first, second) <= 0 ? first : second;
		var high = low == first ? second : first;
		var lowInclusive = this.random.nextBoolean();
		var highInclusive = this.random.nextBoolean();
		NavigableMap<Object, Integer> actualView;
		NavigableMap<Object, Integer> expectedView;
		switch (this.random.nextInt(4)) {
			case 0 -> {
				actualView = this.actual.subMap(low, lowInclusive, high, highInclusive);
				expectedView = this.expected.subMap(low, lowInclusive, high, highInclusive);
			}
			case 1 -> {
				actualView = this.actual.headMap(high, highInclusive);
				expectedView = this.expected.headMap(high, highInclusive);
			}
			case 2 -> {
				actualView = this.actual.tailMap(low, lowInclusive);
				expectedView = this.expected.tailMap(low, lowInclusive);
			}
			default -> {
				actualView = this.actual.subMap(low, lowInclusive, high, highInclusive).descendingMap();
				expectedView = this.expected.subMap(low, lowInclusive, high, highInclusive).descendingMap();
			}
		}
		same("range entries", entriesOf(actualView), entriesOf(expectedView));
		same("range size", actualView.size(), expectedView.size());
		same("range first", actualView.firstEntry(), expectedView.firstEntry());
		same("range last", actualView.lastEntry(), expectedView.lastEntry());
		var probe = this.keyKind.nextKey(this.random);
		same("range ceiling", actualView.ceilingEntry(probe), expectedView.ceilingEntry(probe));
		same("range lower", actualView.lowerEntry(probe), expectedView.lowerEntry(probe));
		same("range descending keys", new ArrayList<>(actualView.descendingKeySet()),
				new ArrayList<>(expectedView.descendingKeySet()));
	}

	private void removeThroughRangeIterator(Object first, Object second) {
		var comparator = comparatorOf(this.expected);
		var low = comparator.compare(first, second) <= 0 ? first : second;
		var high = low == first ? second : first;
		var stride = 1 + this.random.nextInt(3);
		var descending = this.random.nextBoolean();
		var actualKeys = descending ? this.actual.subMap(low, true, high, true).descendingKeySet().iterator()
				: this.actual.subMap(low, true, high, true).keySet().iterator();
		var removed = new ArrayList<Object>();
		var index = 0;
		while (actualKeys.hasNext()) {
			var key = actualKeys.next();
			if (index++ % stride == 0) {
				actualKeys.remove();
				removed.add(key);
			}
		}
		for (var key : removed) {
			this.expected.remove(key);
		}
		this.invariantCheck.run();
	}

	private void drainHalfThroughIterator() {
		var iterator = this.actual.entrySet().iterator();
		var removeNext = true;
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (removeNext) {
				iterator.remove();
				this.expected.remove(entry.getKey());
			}
			removeNext = !removeNext;
		}
	}

	private void requireSameContents() {
		assertThat(entriesOf(this.actual)).as("contents at step %d", this.step).isEqualTo(entriesOf(this.expected));
		assertThat(this.actual.size()).as("size at step %d", this.step).isEqualTo(this.expected.size());
		this.invariantCheck.run();
	}

	private void same(String operation, Object actualResult, Object expectedResult) {
		if (!Objects.equals(actualResult, expectedResult)) {
			assertThat(actualResult).as("%s at step %d", operation, this.step).isEqualTo(expectedResult);
		}
	}

	private static List<Map.Entry<Object, Integer>> entriesOf(NavigableMap<Object, Integer> map) {
		var entries = new ArrayList<Map.Entry<Object, Integer>>();
		for (var entry : map.entrySet()) {
			entries.add(Map.entry(entry.getKey(), entry.getValue()));
		}
		return entries;
	}

	@SuppressWarnings("unchecked")
	private static Comparator<Object> comparatorOf(NavigableMap<Object, Integer> map) {
		Comparator<? super Object> comparator = map.comparator();
		return comparator != null ? comparator::compare
				: (first, second) -> ((Comparable<Object>) first).compareTo(second);
	}

	private static Integer sumOrRemove(Integer existing, Integer given) {
		var sum = existing + given;
		return sum % 5 == 0 ? null : sum;
	}

}
