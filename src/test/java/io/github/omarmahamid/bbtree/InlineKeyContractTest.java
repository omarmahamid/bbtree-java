package io.github.omarmahamid.bbtree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class InlineKeyContractTest {

	private static final int PAIRS = 30_000;

	private static final char[] TRICKY_CHARS = { '\u0000', '\u0001', 'a', 'b', 'ÿ', 'Ā', '翿',
			'耀', '￿' };

	private static final double[] DOUBLE_EDGES = { -0.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE };

	record Encoding(String name, InlineKey<Object> inlineKey, Comparator<Object> order,
			Function<Random, Object> keys) {

		@Override
		public String toString() {
			return this.name;
		}

	}

	static Stream<Encoding> encodings() {
		return Stream.of(
				encoding("strings", InlineKey.forStrings(), Comparator.<String>naturalOrder(),
						InlineKeyContractTest::trickyString),
				encoding("longs", InlineKey.forLongs(), Comparator.<Long>naturalOrder(),
						random -> random.nextBoolean() ? random.nextLong() : random.nextLong(-300, 300)),
				encoding("integers", InlineKey.forIntegers(), Comparator.<Integer>naturalOrder(),
						random -> random.nextBoolean() ? random.nextInt() : random.nextInt(-300, 300)),
				encoding("shorts", InlineKey.forShorts(), Comparator.<Short>naturalOrder(),
						random -> (short) random.nextInt()),
				encoding("bytes", InlineKey.forBytes(), Comparator.<Byte>naturalOrder(), random -> (byte) random.nextInt()),
				encoding("characters", InlineKey.forCharacters(), Comparator.<Character>naturalOrder(),
						random -> (char) random.nextInt(0x1_0000)),
				encoding("doubles", InlineKey.forDoubles(), Comparator.<Double>naturalOrder(),
						InlineKeyContractTest::edgyDouble),
				encoding("floats", InlineKey.forFloats(), Comparator.<Float>naturalOrder(),
						random -> (float) edgyDouble(random)),
				encoding("uuids", InlineKey.forUuids(), Comparator.<UUID>naturalOrder(),
						random -> new UUID(random.nextLong(-2, 2), random.nextBoolean() ? random.nextLong() : random.nextLong(-9, 9))),
				encoding("unsigned byte arrays", InlineKey.forUnsignedByteArrays(), Arrays::compareUnsigned,
						InlineKeyContractTest::byteArray));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("encodings")
	void windowsPastASharedPrefixOrderLikeTheirKeys(Encoding encoding) {
		var random = new Random(encoding.name().hashCode());
		for (var pair = 0; pair < PAIRS; pair++) {
			var first = encoding.keys().apply(random);
			var second = random.nextInt(4) == 0 ? first : encoding.keys().apply(random);
			var comparison = Integer.signum(encoding.order().compare(first, second));
			var shared = encoding.inlineKey().sharedPrefixLength(first, second);
			assertThat(shared).isEqualTo(encoding.inlineKey().sharedPrefixLength(second, first)).isNotNegative();
			var offset = random.nextInt(shared + 1);
			var firstWindow = encoding.inlineKey().windowAt(first, offset);
			var secondWindow = encoding.inlineKey().windowAt(second, offset);
			if (firstWindow != secondWindow) {
				assertThat(Integer.signum(Long.compareUnsigned(firstWindow, secondWindow))).isEqualTo(comparison);
			}
			else if (encoding.inlineKey().windowIdentifiesKey(offset)) {
				assertThat(comparison).isZero();
			}
			if (comparison == 0) {
				assertThat(firstWindow).isEqualTo(secondWindow);
			}
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("encodings")
	void sharedPrefixLengthNeverClaimsBytesThatDiffer(Encoding encoding) {
		var random = new Random(~encoding.name().hashCode());
		for (var pair = 0; pair < PAIRS; pair++) {
			var first = encoding.keys().apply(random);
			var second = encoding.keys().apply(random);
			var shared = Math.min(encoding.inlineKey().sharedPrefixLength(first, second), 64);
			for (var offset = 0; offset < shared; offset++) {
				assertThat(encoding.inlineKey().windowAt(first, offset) >>> (Long.SIZE - Byte.SIZE))
					.isEqualTo(encoding.inlineKey().windowAt(second, offset) >>> (Long.SIZE - Byte.SIZE));
			}
		}
	}

	@Test
	void stringWindowsAreBigEndianUtf16CodeUnitsPaddedWithZeros() {
		var strings = InlineKey.forStrings();
		assertThat(strings.windowAt("AB", 0)).isEqualTo(0x0041_0042_0000_0000L);
		assertThat(strings.windowAt("AB", 1)).isEqualTo(0x4100_4200_0000_0000L);
		assertThat(strings.windowAt("ABCDE", 2)).isEqualTo(0x0042_0043_0044_0045L);
		assertThat(strings.sharedPrefixLength("prefix-a", "prefix-b")).isEqualTo(15);
	}

	@Test
	void longWindowsAreSignFlippedBigEndianBytes() {
		var longs = InlineKey.forLongs();
		assertThat(longs.windowAt(0L, 0)).isEqualTo(Long.MIN_VALUE);
		assertThat(longs.windowAt(-1L, 0)).isEqualTo(Long.MAX_VALUE);
		assertThat(longs.windowAt(0x0102_0304_0506_0708L, 2)).isEqualTo(0x0304_0506_0708_0000L);
		assertThat(longs.windowAt(42L, Long.BYTES)).isZero();
	}

	@Test
	void pathKeysGetNodesThatSkipTheirSharedPrefix() {
		var map = BBTreeMap.<String, Integer>builder().nodeConfig(new NodeConfig(8, 8)).build();
		for (var index = 0; index < 5_000; index++) {
			map.put("/usr/share/doc/package-%05d/README".formatted(index), index);
		}
		var tree = (Tree) map.store();
		assertThat(largestPrefixOffset(tree.root())).isGreaterThanOrEqualTo("/usr/share/doc/package-0".length() * 2);
		assertThat(TreeShape.verifiedEntryCount(tree)).isEqualTo(5_000);
	}

	@Test
	void automaticInlineKeysApplyOnlyToRecognizedNaturallyOrderedKeys() {
		var strings = new BBTreeMap<String, Integer>();
		strings.put("a", 1);
		assertThat(((Tree) strings.store()).root().words).isNotNull();
		var reversed = new BBTreeMap<String, Integer>(Comparator.reverseOrder());
		reversed.put("a", 1);
		assertThat(((Tree) reversed.store()).root().words).isNull();
		var custom = new BBTreeMap<Version, Integer>();
		custom.put(new Version(1), 1);
		assertThat(((Tree) custom.store()).root().words).isNull();
	}

	private static int largestPrefixOffset(Node node) {
		var largest = node.prefixOffset();
		if (node instanceof InternalNode internal) {
			for (var child = 0; child <= internal.size(); child++) {
				largest = Math.max(largest, largestPrefixOffset(internal.childAt(child)));
			}
		}
		return largest;
	}

	@SuppressWarnings("unchecked")
	private static <K> Encoding encoding(String name, InlineKey<K> inlineKey, Comparator<K> order,
			Function<Random, Object> keys) {
		return new Encoding(name, (InlineKey<Object>) inlineKey, (Comparator<Object>) order, keys);
	}

	private static Object trickyString(Random random) {
		var length = random.nextInt(12);
		var builder = new StringBuilder();
		for (var index = 0; index < length; index++) {
			builder.append(TRICKY_CHARS[random.nextInt(TRICKY_CHARS.length)]);
		}
		return builder.toString();
	}

	private static double edgyDouble(Random random) {
		return random.nextInt(4) == 0 ? DOUBLE_EDGES[random.nextInt(DOUBLE_EDGES.length)]
				: random.nextInt(-50, 50) / 4.0;
	}

	private static Object byteArray(Random random) {
		var bytes = new byte[random.nextInt(20)];
		for (var index = 0; index < bytes.length; index++) {
			bytes[index] = (byte) (random.nextBoolean() ? random.nextInt(3) : random.nextInt());
		}
		return bytes;
	}

	private record Version(int number) implements Comparable<Version> {

		@Override
		public int compareTo(Version other) {
			return Integer.compare(this.number, other.number);
		}

	}

}
