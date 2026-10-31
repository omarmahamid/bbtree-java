package io.github.omarmahamid.bbtree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ConcurrentStressTest {

	private static final int THREADS = Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors()));

	private static final int KEY_SPACE = 20_000;

	private static final int OPERATIONS_PER_THREAD = 250_000;

	static Stream<NodeConfig> nodeConfigs() {
		return Stream.of(new NodeConfig(4, 4), new NodeConfig(8, 5), NodeConfig.defaults());
	}

	@ParameterizedTest
	@MethodSource("nodeConfigs")
	void writersOfInterleavedKeysNeverLoseOrInventEntries(NodeConfig config) throws InterruptedException {
		var map = ConcurrentBBTreeMap.<Long, Long>builder().nodeConfig(config).build();
		var expectedPerThread = new ArrayList<TreeMap<Long, Long>>();
		for (var thread = 0; thread < THREADS; thread++) {
			expectedPerThread.add(new TreeMap<>());
		}
		runConcurrently(thread -> {
			var random = new Random(thread);
			var expected = expectedPerThread.get(thread);
			for (var operation = 0; operation < OPERATIONS_PER_THREAD; operation++) {
				var key = (long) random.nextInt(KEY_SPACE / THREADS) * THREADS + thread;
				var value = random.nextLong();
				switch (random.nextInt(5)) {
					case 0, 1 -> assertThat(map.put(key, value)).isEqualTo(expected.put(key, value));
					case 2 -> assertThat(map.remove(key)).isEqualTo(expected.remove(key));
					case 3 -> assertThat(map.get(key)).isEqualTo(expected.get(key));
					default -> assertThat(map.putIfAbsent(key, value)).isEqualTo(expected.putIfAbsent(key, value));
				}
			}
		});
		var union = new TreeMap<Long, Long>();
		expectedPerThread.forEach(union::putAll);
		assertThat(new ArrayList<>(map.entrySet())).isEqualTo(new ArrayList<>(union.entrySet()));
		assertThat(map.size()).isEqualTo(union.size());
		assertThat(TreeShape.verifiedEntryCount(map.store())).isEqualTo(union.size());
	}

	@ParameterizedTest
	@MethodSource("nodeConfigs")
	void readersAlwaysFindStableKeysWhileWritersReshapeTheTree(NodeConfig config) throws InterruptedException {
		var map = ConcurrentBBTreeMap.<Long, Long>builder().nodeConfig(config).build();
		for (long key = 0; key < KEY_SPACE; key += 10) {
			map.put(key, key * 3);
		}
		var writerCount = THREADS / 2;
		var writersRemaining = new AtomicInteger(writerCount);
		var readerRounds = new AtomicLong();
		runConcurrently(thread -> {
			var random = new Random(thread);
			if (thread < writerCount) {
				for (var operation = 0; operation < OPERATIONS_PER_THREAD; operation++) {
					var key = (long) random.nextInt(KEY_SPACE);
					if (key % 10 == 0) {
						continue;
					}
					if (random.nextBoolean()) {
						map.put(key, -key);
					}
					else {
						map.remove(key);
					}
				}
				writersRemaining.decrementAndGet();
				return;
			}
			while (writersRemaining.get() > 0) {
				readerRounds.incrementAndGet();
				var stableKey = (long) random.nextInt(KEY_SPACE / 10) * 10;
				assertThat(map.get(stableKey)).isEqualTo(stableKey * 3);
				var ceiling = map.ceilingKey(stableKey - 9);
				assertThat(ceiling).isNotNull().isBetween(stableKey - 9, stableKey);
				var floor = map.floorEntry(stableKey + 9);
				assertThat(floor.getKey()).isBetween(stableKey, stableKey + 9);
				assertThat(map.getMany(List.of(stableKey, stableKey + 10 < KEY_SPACE ? stableKey + 10 : 0L)))
					.containsExactly(stableKey * 3, stableKey + 10 < KEY_SPACE ? (stableKey + 10) * 3 : 0L);
			}
		});
		assertThat(readerRounds.get()).isPositive();
		assertThat(TreeShape.verifiedEntryCount(map.store())).isEqualTo(map.size());
	}

	@ParameterizedTest
	@MethodSource("nodeConfigs")
	void iteratorsAndScansSeeStrictlyIncreasingKeysIncludingEveryStableKey(NodeConfig config)
			throws InterruptedException {
		var map = ConcurrentBBTreeMap.<Long, Long>builder().nodeConfig(config).build();
		for (long key = 0; key < KEY_SPACE; key += 7) {
			map.put(key, key);
		}
		var writersRemaining = new AtomicInteger((THREADS + 1) / 2);
		var readerRounds = new AtomicLong();
		runConcurrently(thread -> {
			var random = new Random(thread);
			if (thread % 2 == 0) {
				for (var operation = 0; operation < OPERATIONS_PER_THREAD; operation++) {
					var key = (long) random.nextInt(KEY_SPACE);
					if (key % 7 == 0) {
						continue;
					}
					if (random.nextBoolean()) {
						map.put(key, key);
					}
					else {
						map.remove(key);
					}
				}
				writersRemaining.decrementAndGet();
				return;
			}
			while (writersRemaining.get() > 0) {
				readerRounds.incrementAndGet();
				var stableSeen = 0;
				var previous = Long.MIN_VALUE;
				for (var key : random.nextBoolean() ? map.keySet() : map.descendingMap().descendingKeySet()) {
					assertThat(key).isGreaterThan(previous);
					previous = key;
					stableSeen += key % 7 == 0 ? 1 : 0;
				}
				assertThat(stableSeen).isEqualTo((KEY_SPACE + 6) / 7);
				var last = new long[] { Long.MAX_VALUE };
				var stableDescending = new int[1];
				map.scanDescending(null, true, null, true, (key, value) -> {
					assertThat(key).isLessThan(last[0]).isEqualTo(value);
					last[0] = key;
					stableDescending[0] += key % 7 == 0 ? 1 : 0;
					return null;
				});
				assertThat(stableDescending[0]).isEqualTo((KEY_SPACE + 6) / 7);
			}
		});
		assertThat(readerRounds.get()).isPositive();
		assertThat(TreeShape.verifiedEntryCount(map.store())).isEqualTo(map.size());
	}

	@ParameterizedTest
	@MethodSource("nodeConfigs")
	void appendingThreadsGrowTheRightEdgeWithoutLosingKeys(NodeConfig config) throws InterruptedException {
		var map = ConcurrentBBTreeMap.<Long, Integer>builder().nodeConfig(config).build();
		var perThread = 100_000;
		runConcurrently(thread -> {
			for (var index = 0; index < perThread; index++) {
				map.put((long) index * THREADS + thread, thread);
				if (index % 3 == 0) {
					map.remove((long) (index / 2) * THREADS + thread);
				}
			}
		});
		var expected = new TreeMap<Long, Integer>();
		for (var thread = 0; thread < THREADS; thread++) {
			for (var index = 0; index < perThread; index++) {
				expected.put((long) index * THREADS + thread, thread);
				if (index % 3 == 0) {
					expected.remove((long) (index / 2) * THREADS + thread);
				}
			}
		}
		assertThat(new ArrayList<>(map.entrySet())).isEqualTo(new ArrayList<>(expected.entrySet()));
		assertThat(TreeShape.verifiedEntryCount(map.store())).isEqualTo(expected.size());
	}

	@ParameterizedTest
	@MethodSource("nodeConfigs")
	void mergeComputeAndUpdateAreAtomicPerEntry(NodeConfig config) throws InterruptedException {
		var counters = ConcurrentBBTreeMap.<Integer, Long>builder().nodeConfig(config).build();
		var cells = ConcurrentBBTreeMap.<Integer, long[]>builder().nodeConfig(config).build();
		var hotKeys = 64;
		for (var key = 0; key < hotKeys; key++) {
			cells.put(key, new long[2]);
		}
		var increments = 20_000;
		runConcurrently(thread -> {
			var random = new Random(thread);
			for (var operation = 0; operation < increments; operation++) {
				var key = random.nextInt(hotKeys);
				if (operation % 2 == 0) {
					counters.merge(key, 1L, Long::sum);
				}
				else {
					counters.compute(key, (k, current) -> current == null ? 1L : current + 1);
				}
				cells.update(key, (k, cell) -> {
					cell[0]++;
					cell[1]++;
					return null;
				});
				cells.read(random.nextInt(hotKeys), (k, cell) -> {
					assertThat(cell[0]).isEqualTo(cell[1]);
					return null;
				});
			}
		});
		var total = counters.values().stream().mapToLong(Long::longValue).sum();
		assertThat(total).isEqualTo((long) THREADS * increments);
		var cellTotal = cells.values().stream().mapToLong(cell -> cell[0]).sum();
		assertThat(cellTotal).isEqualTo((long) THREADS * increments);
	}

	@ParameterizedTest
	@MethodSource("nodeConfigs")
	void racingInsertersAgreeOnASingleWinnerPerKey(NodeConfig config) throws InterruptedException {
		var map = ConcurrentBBTreeMap.<Integer, Integer>builder().nodeConfig(config).build();
		var winners = new ConcurrentHashMap<Integer, Integer>();
		runConcurrently(thread -> {
			for (var key = 0; key < KEY_SPACE; key++) {
				if (map.putIfAbsent(key, thread) == null) {
					assertThat(winners.put(key, thread)).isNull();
				}
				var seen = map.getOrInsert(key, -1, (k, value) -> value);
				assertThat(seen).isNotEqualTo(-1);
			}
		});
		assertThat(map).isEqualTo(winners);
		assertThat(TreeShape.verifiedEntryCount(map.store())).isEqualTo(KEY_SPACE);
	}

	@ParameterizedTest
	@MethodSource("nodeConfigs")
	void concurrentDrainingLeavesAnEmptyConsistentTree(NodeConfig config) throws InterruptedException {
		var map = ConcurrentBBTreeMap.<Integer, Integer>builder().nodeConfig(config).build();
		for (var key = 0; key < KEY_SPACE; key++) {
			map.put(key, key);
		}
		runConcurrently(thread -> {
			if (thread % 2 == 0) {
				while (map.pollFirstEntry() != null) {
					Thread.onSpinWait();
				}
			}
			else {
				while (map.pollLastEntry() != null) {
					Thread.onSpinWait();
				}
			}
		});
		assertThat(map).isEmpty();
		assertThat(map.size()).isZero();
		assertThat(TreeShape.verifiedEntryCount(map.store())).isZero();
		map.put(1, 1);
		assertThat(map).containsExactly(Map.entry(1, 1));
	}

	private static void runConcurrently(ThreadBody body) throws InterruptedException {
		var start = new CountDownLatch(1);
		var failure = new AtomicReference<Throwable>();
		var threads = new ArrayList<Thread>();
		for (var index = 0; index < THREADS; index++) {
			var thread = index;
			threads.add(Thread.ofPlatform().start(() -> {
				try {
					start.await();
					body.run(thread);
				}
				catch (Throwable thrown) {
					failure.compareAndSet(null, thrown);
				}
			}));
		}
		start.countDown();
		for (var thread : threads) {
			thread.join();
		}
		if (failure.get() != null) {
			throw new AssertionError("A worker thread failed", failure.get());
		}
	}

	@FunctionalInterface
	private interface ThreadBody {

		void run(int thread) throws Exception;

	}

}
