package io.github.omarmahamid.bbtree;

enum FixedWidthInlineKey implements InlineKey<Object> {

	LONG(Long.BYTES) {
		@Override
		long encode(Object key) {
			return ((Long) key) ^ Long.MIN_VALUE;
		}
	},

	INTEGER(Integer.BYTES) {
		@Override
		long encode(Object key) {
			return ((((Integer) key) ^ Integer.MIN_VALUE) & 0xFFFF_FFFFL) << Integer.SIZE;
		}
	},

	SHORT(Short.BYTES) {
		@Override
		long encode(Object key) {
			return ((((Short) key) ^ Short.MIN_VALUE) & 0xFFFFL) << (Long.SIZE - Short.SIZE);
		}
	},

	BYTE(Byte.BYTES) {
		@Override
		long encode(Object key) {
			return ((((Byte) key) ^ Byte.MIN_VALUE) & 0xFFL) << (Long.SIZE - Byte.SIZE);
		}
	},

	CHARACTER(Character.BYTES) {
		@Override
		long encode(Object key) {
			return ((long) (Character) key) << (Long.SIZE - Character.SIZE);
		}
	},

	DOUBLE(Double.BYTES) {
		@Override
		long encode(Object key) {
			var bits = Double.doubleToLongBits((Double) key);
			var signedOrder = bits ^ ((bits >> (Long.SIZE - 1)) & Long.MAX_VALUE);
			return signedOrder ^ Long.MIN_VALUE;
		}
	},

	FLOAT(Float.BYTES) {
		@Override
		long encode(Object key) {
			var bits = Float.floatToIntBits((Float) key);
			var signedOrder = bits ^ ((bits >> (Integer.SIZE - 1)) & Integer.MAX_VALUE);
			return ((signedOrder ^ Integer.MIN_VALUE) & 0xFFFF_FFFFL) << Integer.SIZE;
		}
	};

	private final int width;

	FixedWidthInlineKey(int width) {
		this.width = width;
	}

	abstract long encode(Object key);

	@Override
	public long windowAt(Object key, int offset) {
		return offset >= this.width ? 0 : encode(key) << (offset * Byte.SIZE);
	}

	@Override
	public int sharedPrefixLength(Object first, Object second) {
		var difference = encode(first) ^ encode(second);
		return Math.min(this.width, Long.numberOfLeadingZeros(difference) / Byte.SIZE);
	}

	@Override
	public boolean windowIdentifiesKey(int offset) {
		return true;
	}

}
