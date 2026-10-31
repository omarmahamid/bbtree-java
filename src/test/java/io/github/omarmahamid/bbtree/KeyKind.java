package io.github.omarmahamid.bbtree;

import java.util.Comparator;
import java.util.Random;
import java.util.UUID;

enum KeyKind {

	DENSE_LONGS {
		@Override
		Object nextKey(Random random) {
			return random.nextLong(-1_000, 1_000);
		}
	},

	SPARSE_LONGS {
		@Override
		Object nextKey(Random random) {
			return random.nextLong();
		}
	},

	INTEGERS_IN_REVERSE {
		@Override
		Object nextKey(Random random) {
			return random.nextInt(3_000);
		}

		@Override
		Comparator<Object> comparator() {
			return castComparator(Comparator.<Integer>reverseOrder());
		}
	},

	PATH_STRINGS {
		@Override
		Object nextKey(Random random) {
			return "/usr/share/doc/package-" + random.nextInt(4_000) + "/README." + random.nextInt(3);
		}
	},

	TRICKY_STRINGS {
		private static final char[] ALPHABET = { '\u0000', 'a', 'b', 'ÿ', 'Ā', '翿', '￿' };

		@Override
		Object nextKey(Random random) {
			var length = random.nextInt(7);
			var builder = new StringBuilder(length);
			for (var index = 0; index < length; index++) {
				builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
			}
			return builder.toString();
		}
	},

	STRINGS_WITH_DECLARED_INLINE_KEY {
		@Override
		Object nextKey(Random random) {
			return "key-" + random.nextInt(5_000);
		}

		@Override
		Comparator<Object> comparator() {
			return castComparator(Comparator.<String>naturalOrder());
		}

		@Override
		InlineKey<Object> inlineKey() {
			return castInlineKey(InlineKey.forStrings());
		}
	},

	UUIDS {
		@Override
		Object nextKey(Random random) {
			return new UUID(random.nextLong(-3, 3), random.nextLong(-500, 500));
		}
	},

	DOUBLES {
		private static final double[] EDGES = { -0.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY,
				Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE };

		@Override
		Object nextKey(Random random) {
			if (random.nextInt(10) == 0) {
				return EDGES[random.nextInt(EDGES.length)];
			}
			return (double) random.nextInt(-2_000, 2_000) / 8;
		}
	},

	CHARACTERS {
		@Override
		Object nextKey(Random random) {
			return (char) random.nextInt(0, 0x1_0000);
		}
	};

	abstract Object nextKey(Random random);

	Comparator<Object> comparator() {
		return null;
	}

	InlineKey<Object> inlineKey() {
		return null;
	}

	BBTreeMap<Object, Integer> newMap(NodeConfig config) {
		var builder = BBTreeMap.<Object, Integer>builder().nodeConfig(config);
		if (comparator() != null) {
			builder.comparator(comparator());
		}
		if (inlineKey() != null) {
			builder.inlineKey(inlineKey());
		}
		return builder.build();
	}

	ConcurrentBBTreeMap<Object, Integer> newConcurrentMap(NodeConfig config) {
		var builder = ConcurrentBBTreeMap.<Object, Integer>builder().nodeConfig(config);
		if (comparator() != null) {
			builder.comparator(comparator());
		}
		if (inlineKey() != null) {
			builder.inlineKey(inlineKey());
		}
		return builder.build();
	}

	BBTreeSet<Object> newSet(NodeConfig config) {
		var builder = BBTreeSet.<Object>builder().nodeConfig(config);
		if (comparator() != null) {
			builder.comparator(comparator());
		}
		if (inlineKey() != null) {
			builder.inlineKey(inlineKey());
		}
		return builder.build();
	}

	@SuppressWarnings("unchecked")
	private static Comparator<Object> castComparator(Comparator<?> comparator) {
		return (Comparator<Object>) comparator;
	}

	@SuppressWarnings("unchecked")
	private static InlineKey<Object> castInlineKey(InlineKey<?> inlineKey) {
		return (InlineKey<Object>) inlineKey;
	}

}
