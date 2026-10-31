package io.github.omarmahamid.bbtree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConcurrentBBTreeMapTest {

	private static ConcurrentBBTreeMap<Long, Long> squares(int count) {
		var map = ConcurrentBBTreeMap.<Long, Long>builder().nodeConfig(new NodeConfig(4, 4)).build();
		for (long key = 0; key < count; key++) {
			map.put(key, key * key);
		}
		return map;
	}

	@Nested
	class Closures {

		@Test
		void readRunsTheReaderOnThePresentEntryAndReturnsNullForAMissingOne() {
			var map = squares(100);
			assertThat(map.<String>read(7L, (key, value) -> key + ":" + value)).isEqualTo("7:49");
			assertThat(map.<String>read(700L, (key, value) -> "unreachable")).isNull();
		}

		@Test
		void updateMutatesTheValueInPlaceUnderTheEntryLatch() {
			var map = new ConcurrentBBTreeMap<String, AtomicLong>();
			map.put("hits", new AtomicLong());
			assertThat(map.<Long>update("hits", (key, counter) -> counter.addAndGet(5))).isEqualTo(5L);
			assertThat(map.get("hits")).hasValue(5L);
			assertThat(map.<Long>update("misses", (key, counter) -> counter.incrementAndGet())).isNull();
		}

		@Test
		void getOrInsertInsertsOnlyWhenAbsentAndReadsTheStoredEntry() {
			var map = new ConcurrentBBTreeMap<String, Integer>();
			assertThat(map.<Integer>getOrInsert("a", 1, (key, value) -> value)).isEqualTo(1);
			assertThat(map.<Integer>getOrInsert("a", 2, (key, value) -> value)).isEqualTo(1);
			assertThat(map).containsExactly(Map.entry("a", 1));
		}

		@Test
		void removeIfRemovesOnlyWhenTheConditionHoldsOfTheCurrentEntry() {
			var map = squares(10);
			assertThat(map.removeIf(3L, (key, value) -> value > 100)).isNull();
			assertThat(map.removeIf(3L, (key, value) -> value == 9)).isEqualTo(Map.entry(3L, 9L));
			assertThat(map).doesNotContainKey(3L);
		}

		@Test
		void aThrowingClosureLeavesTheEntryLatchFree() {
			var map = squares(10);
			assertThatThrownBy(() -> map.update(2L, (key, value) -> {
				throw new IllegalStateException("closure failure");
			})).hasMessage("closure failure");
			assertThatThrownBy(() -> map.compute(2L, (key, value) -> {
				throw new IllegalStateException("compute failure");
			})).hasMessage("compute failure");
			assertThat(map.put(2L, 0L)).isEqualTo(4L);
			assertThat(map.<Long>read(2L, (key, value) -> value)).isZero();
		}

	}

	@Nested
	class Scans {

		@Test
		void scanVisitsTheRangeInOrderUntilTheVisitorReturnsAResult() {
			var map = squares(1_000);
			var visited = new ArrayList<Long>();
			String result = map.scan(10L, true, 20L, false, (key, value) -> {
				visited.add(key);
				return key == 15L ? "stopped at " + value : null;
			});
			assertThat(result).isEqualTo("stopped at 225");
			assertThat(visited).containsExactly(10L, 11L, 12L, 13L, 14L, 15L);
		}

		@Test
		void scanDescendingVisitsTheSameRangeInReverse() {
			var map = squares(1_000);
			var visited = new ArrayList<Long>();
			assertThat(map.<Object>scanDescending(500L, false, 510L, true, (key, value) -> {
				visited.add(key);
				return null;
			})).isNull();
			assertThat(visited).containsExactly(510L, 509L, 508L, 507L, 506L, 505L, 504L, 503L, 502L, 501L);
		}

		@Test
		void scanWithNullBoundsCoversTheWholeMap() {
			var map = squares(777);
			var count = new long[1];
			map.scan(null, true, null, true, (key, value) -> {
				count[0]++;
				return null;
			});
			assertThat(count[0]).isEqualTo(777);
		}

		@Test
		void scanRejectsAnInvertedRange() {
			var map = squares(10);
			assertThatThrownBy(() -> map.scan(5L, true, 1L, true, (key, value) -> null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("fromKey > toKey");
		}

	}

	@Nested
	class Batches {

		@Test
		void getManyAnswersEveryKeyInOrderWithNullForMissingKeys() {
			var map = squares(5_000);
			var keys = new ArrayList<Long>();
			for (long key = -3; key < 5_010; key += 3) {
				keys.add(key);
			}
			var values = map.getMany(keys);
			for (var index = 0; index < keys.size(); index++) {
				assertThat(values.get(index)).isEqualTo(map.get(keys.get(index)));
			}
		}

		@Test
		void readManyAndPutManyMatchTheirSingleEntryCounterparts() {
			var map = squares(10);
			assertThat(map.<Long>readMany(List.of(1L, 2L, 99L), (key, value) -> key + value)).containsExactly(2L, 6L, null);
			assertThat(map.putMany(List.of(Map.entry(1L, 0L), Map.entry(50L, 1L)))).containsExactly(1L, null);
			assertThat(map).containsEntry(1L, 0L).containsEntry(50L, 1L);
		}

		@Test
		void getManyRejectsANullKey() {
			var map = squares(10);
			var keys = new ArrayList<Long>();
			keys.add(1L);
			keys.add(null);
			assertThatThrownBy(() -> map.getMany(keys)).isInstanceOf(NullPointerException.class);
		}

	}

	@Nested
	class NullPolicy {

		@Test
		void nullKeysAndValuesAreRejectedLikeConcurrentSkipListMap() {
			var map = new ConcurrentBBTreeMap<String, String>();
			assertThatThrownBy(() -> map.put(null, "v")).isInstanceOf(NullPointerException.class)
				.hasMessage("A concurrent map can not hold a null key");
			assertThatThrownBy(() -> map.put("k", null)).isInstanceOf(NullPointerException.class)
				.hasMessage("A concurrent map can not hold a null value");
			assertThatThrownBy(() -> map.get(null)).isInstanceOf(NullPointerException.class);
		}

	}

	@Nested
	class Copies {

		@Test
		void cloneIsIndependentOfTheOriginal() {
			var map = squares(300);
			var copy = map.clone();
			copy.remove(5L);
			map.put(1_000L, 1L);
			assertThat(map).containsKey(5L).hasSize(301);
			assertThat(copy).doesNotContainKey(5L).doesNotContainKey(1_000L).hasSize(299);
			assertThat(TreeShape.verifiedEntryCount(copy.store())).isEqualTo(299);
		}

		@Test
		void constructingFromASortedMapLoadsItInBulk() {
			var source = new java.util.TreeMap<Long, Long>();
			for (long key = 0; key < 10_000; key++) {
				source.put(key, -key);
			}
			var map = new ConcurrentBBTreeMap<>(source);
			assertThat(map).isEqualTo(source);
			assertThat(TreeShape.verifiedEntryCount(map.store())).isEqualTo(10_000);
		}

	}

}
