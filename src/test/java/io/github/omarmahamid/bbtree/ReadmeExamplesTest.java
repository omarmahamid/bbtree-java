package io.github.omarmahamid.bbtree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

class ReadmeExamplesTest {

	@Test
	void usageExampleBehavesAsTheReadmeSays() {
		NavigableMap<String, Integer> sorted = new BBTreeMap<>();
		sorted.put("two", 2);
		sorted.put("one", 1);
		assertThat(sorted.firstKey()).isEqualTo("one");

		var shared = new ConcurrentBBTreeMap<Long, Long>();
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			executor.submit(() -> shared.put(1L, 10L));
			executor.submit(() -> shared.put(2L, 20L));
		}
		assertThat(shared.<Long>read(2L, (key, value) -> value + 1)).isEqualTo(21L);
		assertThat(shared.getMany(List.of(1L, 2L, 3L))).containsExactly(10L, 20L, null);
		assertThat(shared.merge(1L, 5L, Long::sum)).isEqualTo(15L);
	}

	@Test
	void tuningExampleOrdersByteArraysUnsigned() {
		var map = BBTreeMap.<byte[], String>builder()
			.comparator(Arrays::compareUnsigned)
			.inlineKey(InlineKey.forUnsignedByteArrays())
			.nodeConfig(new NodeConfig(64, 64))
			.build();
		map.put(new byte[] { (byte) 0xFF }, "high");
		map.put(new byte[] { 0x01 }, "low");
		map.put(new byte[] { 0x01, 0x00 }, "low and longer");
		assertThat(map.values()).containsExactly("low", "low and longer", "high");
	}

}
