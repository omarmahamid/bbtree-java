package io.github.omarmahamid.bbtree;

enum StringInlineKey implements InlineKey<String> {

	INSTANCE;

	private static final int CHARS_PER_WINDOW = Long.BYTES / Character.BYTES;

	private static final int HIGH_BYTE_MASK = 0xFF00;

	@Override
	public long windowAt(String key, int offset) {
		var firstChar = offset >>> 1;
		var window = 0L;
		for (var index = 0; index < CHARS_PER_WINDOW; index++) {
			window = (window << Character.SIZE) | charOrPadding(key, firstChar + index);
		}
		if ((offset & 1) != 0) {
			window = (window << Byte.SIZE) | (charOrPadding(key, firstChar + CHARS_PER_WINDOW) >>> Byte.SIZE);
		}
		return window;
	}

	@Override
	public int sharedPrefixLength(String first, String second) {
		var limit = Math.min(first.length(), second.length());
		var index = 0;
		while (index < limit && first.charAt(index) == second.charAt(index)) {
			index++;
		}
		if (index == limit) {
			return index * Character.BYTES;
		}
		var difference = first.charAt(index) ^ second.charAt(index);
		var highBytesAgree = (difference & HIGH_BYTE_MASK) == 0;
		return index * Character.BYTES + (highBytesAgree ? 1 : 0);
	}

	private static long charOrPadding(String key, int index) {
		return index < key.length() ? key.charAt(index) : 0;
	}

}
