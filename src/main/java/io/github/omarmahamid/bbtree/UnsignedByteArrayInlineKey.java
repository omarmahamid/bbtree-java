package io.github.omarmahamid.bbtree;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

enum UnsignedByteArrayInlineKey implements InlineKey<byte[]> {

	INSTANCE;

	private static final VarHandle BIG_ENDIAN_LONG = MethodHandles.byteArrayViewVarHandle(long[].class,
			ByteOrder.BIG_ENDIAN);

	private static final int UNSIGNED_BYTE_MASK = 0xFF;

	@Override
	public long windowAt(byte[] key, int offset) {
		if (offset + Long.BYTES <= key.length) {
			return (long) BIG_ENDIAN_LONG.get(key, offset);
		}
		var window = 0L;
		for (var index = offset; index < offset + Long.BYTES; index++) {
			var current = index < key.length ? key[index] & UNSIGNED_BYTE_MASK : 0;
			window = (window << Byte.SIZE) | current;
		}
		return window;
	}

	@Override
	public int sharedPrefixLength(byte[] first, byte[] second) {
		var mismatch = Arrays.mismatch(first, second);
		return mismatch < 0 ? first.length : mismatch;
	}

}
