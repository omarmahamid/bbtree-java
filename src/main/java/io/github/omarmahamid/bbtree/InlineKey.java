package io.github.omarmahamid.bbtree;

import java.io.Serializable;
import java.util.UUID;

public interface InlineKey<K> extends Serializable {

	int SHARED_PREFIX_SCAN_LIMIT = 256;

	long windowAt(K key, int offset);

	default int sharedPrefixLength(K first, K second) {
		for (var offset = 0; offset < SHARED_PREFIX_SCAN_LIMIT; offset += Long.BYTES) {
			var difference = windowAt(first, offset) ^ windowAt(second, offset);
			if (difference != 0) {
				return offset + Long.numberOfLeadingZeros(difference) / Byte.SIZE;
			}
		}
		return SHARED_PREFIX_SCAN_LIMIT;
	}

	default boolean windowIdentifiesKey(int offset) {
		return false;
	}

	static InlineKey<String> forStrings() {
		return StringInlineKey.INSTANCE;
	}

	static InlineKey<byte[]> forUnsignedByteArrays() {
		return UnsignedByteArrayInlineKey.INSTANCE;
	}

	static InlineKey<UUID> forUuids() {
		return UuidInlineKey.INSTANCE;
	}

	static InlineKey<Long> forLongs() {
		return fixedWidth(FixedWidthInlineKey.LONG);
	}

	static InlineKey<Integer> forIntegers() {
		return fixedWidth(FixedWidthInlineKey.INTEGER);
	}

	static InlineKey<Short> forShorts() {
		return fixedWidth(FixedWidthInlineKey.SHORT);
	}

	static InlineKey<Byte> forBytes() {
		return fixedWidth(FixedWidthInlineKey.BYTE);
	}

	static InlineKey<Character> forCharacters() {
		return fixedWidth(FixedWidthInlineKey.CHARACTER);
	}

	static InlineKey<Double> forDoubles() {
		return fixedWidth(FixedWidthInlineKey.DOUBLE);
	}

	static InlineKey<Float> forFloats() {
		return fixedWidth(FixedWidthInlineKey.FLOAT);
	}

	@SuppressWarnings("unchecked")
	private static <K> InlineKey<K> fixedWidth(FixedWidthInlineKey encoding) {
		return (InlineKey<K>) (InlineKey<?>) encoding;
	}

}
