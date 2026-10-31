package io.github.omarmahamid.bbtree;

record KeyRange(Object low, boolean lowInclusive, Object high, boolean highInclusive) {

	static final KeyRange UNBOUNDED = new KeyRange(Node.NO_FENCE, false, Node.NO_FENCE, false);

	boolean hasLow() {
		return this.low != Node.NO_FENCE;
	}

	boolean hasHigh() {
		return this.high != Node.NO_FENCE;
	}

	boolean isUnbounded() {
		return !hasLow() && !hasHigh();
	}

	boolean tooLow(KeyOrder order, Object key) {
		if (!hasLow()) {
			return false;
		}
		var comparison = order.compare(key, this.low);
		return comparison < 0 || (comparison == 0 && !this.lowInclusive);
	}

	boolean tooHigh(KeyOrder order, Object key) {
		if (!hasHigh()) {
			return false;
		}
		var comparison = order.compare(key, this.high);
		return comparison > 0 || (comparison == 0 && !this.highInclusive);
	}

	boolean contains(KeyOrder order, Object key) {
		return !tooLow(order, key) && !tooHigh(order, key);
	}

	boolean containsClosed(KeyOrder order, Object key) {
		var aboveLow = !hasLow() || order.compare(key, this.low) >= 0;
		var belowHigh = !hasHigh() || order.compare(key, this.high) <= 0;
		return aboveLow && belowHigh;
	}

	boolean admits(KeyOrder order, Object key, boolean inclusive) {
		return inclusive ? contains(order, key) : containsClosed(order, key);
	}

	KeyRange withLow(Object newLow, boolean inclusive) {
		return new KeyRange(newLow, inclusive, this.high, this.highInclusive);
	}

	KeyRange withHigh(Object newHigh, boolean inclusive) {
		return new KeyRange(this.low, this.lowInclusive, newHigh, inclusive);
	}

}
