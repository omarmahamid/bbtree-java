package io.github.omarmahamid.bbtree;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

final class KeyOrder {

	private final Comparator<Object> comparator;

	private final InlineKey<Object> declaredInlineKey;

	private InlineKey<Object> inlineKey;

	private volatile boolean inlineKeyResolved;

	@SuppressWarnings("unchecked")
	KeyOrder(Comparator<?> comparator, InlineKey<?> declaredInlineKey) {
		this.comparator = (Comparator<Object>) comparator;
		this.declaredInlineKey = (InlineKey<Object>) declaredInlineKey;
		if (declaredInlineKey != null || comparator != null) {
			this.inlineKey = this.declaredInlineKey;
			this.inlineKeyResolved = true;
		}
	}

	KeyOrder unresolvedCopy() {
		return new KeyOrder(this.comparator, this.declaredInlineKey);
	}

	Comparator<Object> comparator() {
		return this.comparator;
	}

	InlineKey<Object> declaredInlineKey() {
		return this.declaredInlineKey;
	}

	@SuppressWarnings("unchecked")
	int compare(Object first, Object second) {
		return this.comparator == null ? ((Comparable<Object>) first).compareTo(second)
				: this.comparator.compare(first, second);
	}

	void requireSearchable(Object key) {
		if (this.comparator == null) {
			Objects.requireNonNull(key, "A naturally ordered map can not hold a null key");
		}
	}

	void requireInsertable(Object key) {
		compare(key, key);
	}

	boolean hasInlineKeys() {
		return this.inlineKeyResolved && this.inlineKey != null;
	}

	synchronized void resolveInlineKeyFor(Object firstKey) {
		if (this.inlineKeyResolved) {
			return;
		}
		this.inlineKey = automaticInlineKeyFor(firstKey.getClass());
		this.inlineKeyResolved = true;
	}

	long storedWord(Object key, int offset) {
		return this.inlineKey.windowAt(key, offset) ^ Long.MIN_VALUE;
	}

	boolean windowIdentifiesKey(int offset) {
		return this.inlineKey.windowIdentifiesKey(offset);
	}

	int sharedPrefixLength(Object lowFence, Object highFence) {
		if (lowFence == Node.NO_FENCE || highFence == Node.NO_FENCE) {
			return 0;
		}
		return this.inlineKey.sharedPrefixLength(lowFence, highFence);
	}

	int binarySearch(Object[] keys, int from, int to, Object key) {
		var low = from;
		var high = to - 1;
		while (low <= high) {
			var middle = (low + high) >>> 1;
			var comparison = compare(keys[middle], key);
			if (comparison < 0) {
				low = middle + 1;
			}
			else if (comparison > 0) {
				high = middle - 1;
			}
			else {
				return middle;
			}
		}
		return -(low + 1);
	}

	@SuppressWarnings("unchecked")
	private static InlineKey<Object> automaticInlineKeyFor(Class<?> keyClass) {
		InlineKey<?> chosen;
		if (keyClass == String.class) {
			chosen = InlineKey.forStrings();
		}
		else if (keyClass == Long.class) {
			chosen = InlineKey.forLongs();
		}
		else if (keyClass == Integer.class) {
			chosen = InlineKey.forIntegers();
		}
		else if (keyClass == UUID.class) {
			chosen = InlineKey.forUuids();
		}
		else if (keyClass == Double.class) {
			chosen = InlineKey.forDoubles();
		}
		else if (keyClass == Float.class) {
			chosen = InlineKey.forFloats();
		}
		else if (keyClass == Short.class) {
			chosen = InlineKey.forShorts();
		}
		else if (keyClass == Character.class) {
			chosen = InlineKey.forCharacters();
		}
		else if (keyClass == Byte.class) {
			chosen = InlineKey.forBytes();
		}
		else {
			chosen = null;
		}
		return (InlineKey<Object>) chosen;
	}

}
