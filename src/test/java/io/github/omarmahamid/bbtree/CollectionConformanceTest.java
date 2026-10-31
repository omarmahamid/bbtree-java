package io.github.omarmahamid.bbtree;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.stream.Stream;

import com.google.common.collect.testing.ConcurrentNavigableMapTestSuiteBuilder;
import com.google.common.collect.testing.NavigableMapTestSuiteBuilder;
import com.google.common.collect.testing.NavigableSetTestSuiteBuilder;
import com.google.common.collect.testing.TestStringSortedMapGenerator;
import com.google.common.collect.testing.TestStringSortedSetGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;
import com.google.common.collect.testing.features.SetFeature;
import junit.framework.Test;
import junit.framework.TestFailure;
import junit.framework.TestResult;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class CollectionConformanceTest {

	private static final NodeConfig TINY_NODES = new NodeConfig(4, 4);

	private static final int REPORTED_FAILURE_LIMIT = 40;

	@TestFactory
	Stream<DynamicTest> guavaTestlibSuitesPassForEveryCollection() {
		return Stream
			.of(mapSuite("BBTreeMap natural", NodeConfig.defaults()),
					mapSuite("BBTreeMap natural, tiny nodes", TINY_NODES), nullFriendlyMapSuite(),
					setSuite("BBTreeSet natural", NodeConfig.defaults()),
					setSuite("BBTreeSet natural, tiny nodes", TINY_NODES),
					concurrentMapSuite("ConcurrentBBTreeMap natural", NodeConfig.defaults()),
					concurrentMapSuite("ConcurrentBBTreeMap natural, tiny nodes", TINY_NODES),
					concurrentSetSuite())
			.map(suite -> DynamicTest.dynamicTest(suite.toString(), () -> requirePassing(suite)));
	}

	private static void requirePassing(Test suite) {
		var result = new TestResult();
		suite.run(result);
		var failures = new ArrayList<String>();
		describeFailures(result.failures(), failures);
		describeFailures(result.errors(), failures);
		assertThat(failures).as("%d of %d conformance tests failed", failures.size(), result.runCount()).isEmpty();
		assertThat(result.runCount()).isPositive();
	}

	private static void describeFailures(Enumeration<TestFailure> source, List<String> into) {
		while (source.hasMoreElements() && into.size() < REPORTED_FAILURE_LIMIT) {
			var failure = source.nextElement();
			into.add(failure.failedTest() + " -> " + failure.thrownException());
		}
	}

	private static Test mapSuite(String name, NodeConfig config) {
		return NavigableMapTestSuiteBuilder.using(new TestStringSortedMapGenerator() {
			@Override
			protected SortedMap<String, String> create(Map.Entry<String, String>[] entries) {
				return populated(BBTreeMap.<String, String>builder().nodeConfig(config).build(), entries);
			}
		})
			.named(name)
			.withFeatures(MapFeature.GENERAL_PURPOSE, MapFeature.ALLOWS_NULL_VALUES,
					MapFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION, CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
					CollectionFeature.KNOWN_ORDER, CollectionFeature.SERIALIZABLE, CollectionSize.ANY)
			.createTestSuite();
	}

	private static Test nullFriendlyMapSuite() {
		return NavigableMapTestSuiteBuilder.using(new TestStringSortedMapGenerator() {
			@Override
			protected SortedMap<String, String> create(Map.Entry<String, String>[] entries) {
				return populated(new BBTreeMap<String, String>(new NullFriendlyComparator<>()), entries);
			}
		})
			.named("BBTreeMap with a null-friendly comparator")
			.withFeatures(MapFeature.GENERAL_PURPOSE, MapFeature.ALLOWS_NULL_KEYS, MapFeature.ALLOWS_NULL_VALUES,
					MapFeature.ALLOWS_ANY_NULL_QUERIES, MapFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION,
					CollectionFeature.SUPPORTS_ITERATOR_REMOVE, CollectionFeature.KNOWN_ORDER,
					CollectionFeature.SERIALIZABLE, CollectionSize.ANY)
			.createTestSuite();
	}

	private static Test setSuite(String name, NodeConfig config) {
		return NavigableSetTestSuiteBuilder.using(new TestStringSortedSetGenerator() {
			@Override
			protected SortedSet<String> create(String[] elements) {
				var set = BBTreeSet.<String>builder().nodeConfig(config).build();
				for (var element : elements) {
					set.add(element);
				}
				return set;
			}
		})
			.named(name)
			.withFeatures(SetFeature.GENERAL_PURPOSE, CollectionFeature.SERIALIZABLE, CollectionFeature.KNOWN_ORDER,
					CollectionFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION, CollectionSize.ANY)
			.createTestSuite();
	}

	private static Test concurrentMapSuite(String name, NodeConfig config) {
		return ConcurrentNavigableMapTestSuiteBuilder.using(new TestStringSortedMapGenerator() {
			@Override
			protected SortedMap<String, String> create(Map.Entry<String, String>[] entries) {
				return populated(ConcurrentBBTreeMap.<String, String>builder().nodeConfig(config).build(), entries);
			}
		})
			.named(name)
			.withFeatures(MapFeature.GENERAL_PURPOSE, CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
					CollectionFeature.KNOWN_ORDER, CollectionFeature.SERIALIZABLE, CollectionSize.ANY)
			.createTestSuite();
	}

	private static Test concurrentSetSuite() {
		return NavigableSetTestSuiteBuilder.using(new TestStringSortedSetGenerator() {
			@Override
			protected SortedSet<String> create(String[] elements) {
				var set = new ConcurrentBBTreeSet<String>();
				for (var element : elements) {
					set.add(element);
				}
				return set;
			}
		})
			.named("ConcurrentBBTreeSet natural")
			.withFeatures(SetFeature.GENERAL_PURPOSE, CollectionFeature.SERIALIZABLE, CollectionFeature.KNOWN_ORDER,
					CollectionSize.ANY)
			.createTestSuite();
	}

	private static <M extends Map<String, String>> M populated(M map, Map.Entry<String, String>[] entries) {
		for (var entry : entries) {
			map.put(entry.getKey(), entry.getValue());
		}
		return map;
	}

	private static final class NullFriendlyComparator<T> implements Comparator<T>, Serializable {

		@Override
		public int compare(T left, T right) {
			return String.valueOf(left).compareTo(String.valueOf(right));
		}

	}

}
