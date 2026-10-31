package io.github.omarmahamid.bbtree;

import java.util.UUID;

enum UuidInlineKey implements InlineKey<UUID> {

	INSTANCE;

	private static final int ENCODED_LENGTH = 2 * Long.BYTES;

	@Override
	public long windowAt(UUID key, int offset) {
		var high = key.getMostSignificantBits() ^ Long.MIN_VALUE;
		var low = key.getLeastSignificantBits() ^ Long.MIN_VALUE;
		if (offset == 0) {
			return high;
		}
		if (offset < Long.BYTES) {
			var shift = offset * Byte.SIZE;
			return (high << shift) | (low >>> (Long.SIZE - shift));
		}
		if (offset < ENCODED_LENGTH) {
			return low << ((offset - Long.BYTES) * Byte.SIZE);
		}
		return 0;
	}

	@Override
	public int sharedPrefixLength(UUID first, UUID second) {
		var highDifference = first.getMostSignificantBits() ^ second.getMostSignificantBits();
		if (highDifference != 0) {
			return Long.numberOfLeadingZeros(highDifference) / Byte.SIZE;
		}
		var lowDifference = first.getLeastSignificantBits() ^ second.getLeastSignificantBits();
		return Long.BYTES + Math.min(Long.BYTES, Long.numberOfLeadingZeros(lowDifference) / Byte.SIZE);
	}

	@Override
	public boolean windowIdentifiesKey(int offset) {
		return offset >= Long.BYTES;
	}

}
