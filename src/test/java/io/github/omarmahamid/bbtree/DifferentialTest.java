package io.github.omarmahamid.bbtree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DifferentialTest {

	private static final int OPERATIONS = 40_000;

	static Stream<Arguments> keyKindsAndNodeConfigs() {
		var configs = new NodeConfig[] { new NodeConfig(4, 4), new NodeConfig(5, 7), new NodeConfig(16, 6),
				NodeConfig.defaults() };
		return Stream.of(KeyKind.values())
			.flatMap(keyKind -> Stream.of(configs).map(config -> Arguments.of(keyKind, config)));
	}

	@ParameterizedTest(name = "{0} with {1}")
	@MethodSource("keyKindsAndNodeConfigs")
	void bbtreeMapAgreesWithTreeMapOnRandomOperations(KeyKind keyKind, NodeConfig config) {
		var actual = keyKind.newMap(config);
		var expected = new TreeMap<Object, Integer>(keyKind.comparator());
		new RandomizedMapCheck(actual, expected, keyKind, seedFor(keyKind, config),
				() -> assertThat(TreeShape.verifiedEntryCount(actual.store())).isEqualTo(expected.size()))
			.run(OPERATIONS);
	}

	@ParameterizedTest(name = "{0} with {1}")
	@MethodSource("keyKindsAndNodeConfigs")
	void concurrentMapAgreesWithConcurrentSkipListMapOnRandomOperations(KeyKind keyKind, NodeConfig config) {
		var actual = keyKind.newConcurrentMap(config);
		var expected = new ConcurrentSkipListMap<Object, Integer>(keyKind.comparator());
		new RandomizedMapCheck(actual, expected, keyKind, seedFor(keyKind, config),
				() -> assertThat(TreeShape.verifiedEntryCount(actual.store())).isEqualTo(expected.size()))
			.run(OPERATIONS);
	}

	@ParameterizedTest(name = "{0} with {1}")
	@MethodSource("keyKindsAndNodeConfigs")
	void bbtreeSetAgreesWithTreeSetOnRandomOperations(KeyKind keyKind, NodeConfig config) {
		var actual = keyKind.newSet(config);
		var expected = new TreeSet<Object>(keyKind.comparator());
		var random = new Random(seedFor(keyKind, config));
		for (var step = 0; step < OPERATIONS; step++) {
			var element = keyKind.nextKey(random);
			switch (random.nextInt(6)) {
				case 0, 1 -> assertThat(actual.add(element)).isEqualTo(expected.add(element));
				case 2 -> assertThat(actual.remove(element)).isEqualTo(expected.remove(element));
				case 3 -> assertThat(actual.contains(element)).isEqualTo(expected.contains(element));
				case 4 -> {
					assertThat(actual.ceiling(element)).isEqualTo(expected.ceiling(element));
					assertThat(actual.lower(element)).isEqualTo(expected.lower(element));
				}
				default -> assertThat(actual.size()).isEqualTo(expected.size());
			}
		}
		assertThat(new ArrayList<>(actual)).isEqualTo(new ArrayList<>(expected));
		assertThat(new ArrayList<>(actual.descendingSet())).isEqualTo(new ArrayList<>(expected.descendingSet()));
		assertThat(TreeShape.verifiedEntryCount(actual.map().store())).isEqualTo(expected.size());
	}

	private static long seedFor(KeyKind keyKind, NodeConfig config) {
		return 31L * keyKind.ordinal() + config.leafCapacity() * 7L + config.internalCapacity();
	}

}
