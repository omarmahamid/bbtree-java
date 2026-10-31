package io.github.omarmahamid.bbtree.bench;

import java.util.HashSet;
import java.util.Random;

public enum KeyShape {

	LONG {
		@Override
		Object randomKey(Random random) {
			return random.nextLong();
		}

		@Override
		Object equalButDistinct(Object key) {
			return Long.valueOf((Long) key);
		}
	},

	STRING {
		private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

		private static final int LENGTH = 16;

		@Override
		Object randomKey(Random random) {
			var builder = new StringBuilder(LENGTH);
			for (var index = 0; index < LENGTH; index++) {
				builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
			}
			return builder.toString();
		}

		@Override
		Object equalButDistinct(Object key) {
			return new String(((String) key).toCharArray());
		}
	};

	abstract Object randomKey(Random random);

	abstract Object equalButDistinct(Object key);

	Object[] distinctKeys(int count, long seed) {
		var random = new Random(seed);
		var seen = new HashSet<Object>(count * 2);
		var keys = new Object[count];
		var filled = 0;
		while (filled < count) {
			var key = randomKey(random);
			if (seen.add(key)) {
				keys[filled++] = key;
			}
		}
		return keys;
	}

	Object[] probesOf(Object[] keys, int count, long seed) {
		var random = new Random(seed);
		var probes = new Object[count];
		for (var index = 0; index < count; index++) {
			probes[index] = equalButDistinct(keys[random.nextInt(keys.length)]);
		}
		return probes;
	}

}
