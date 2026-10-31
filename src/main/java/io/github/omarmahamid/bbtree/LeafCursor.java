package io.github.omarmahamid.bbtree;

final class LeafCursor {

	private final LeafSource source;

	private final LeafWindow window = new LeafWindow();

	private int slot;

	LeafCursor(LeafSource source) {
		this.source = source;
	}

	boolean seek(Object key, Relation relation) {
		this.source.loadLeaf(this.window, key, Routing.COVERING);
		this.slot = relation.slotIn(this.window, this.source.order(), key);
		return relation.ascending() ? settleForward() : settleBackward();
	}

	boolean seekRangeStart(KeyRange range, boolean descending) {
		if (descending) {
			return range.hasHigh() ? seek(range.high(), range.highInclusive() ? Relation.FLOOR : Relation.LOWER)
					: seekLast();
		}
		return range.hasLow() ? seek(range.low(), range.lowInclusive() ? Relation.CEILING : Relation.HIGHER)
				: seekFirst();
	}

	boolean seekFirst() {
		this.source.loadLeaf(this.window, null, Routing.LEFTMOST);
		this.slot = 0;
		return settleForward();
	}

	boolean seekLast() {
		this.source.loadLeaf(this.window, null, Routing.RIGHTMOST);
		this.slot = this.window.size() - 1;
		return settleBackward();
	}

	boolean advance() {
		this.slot++;
		return settleForward();
	}

	boolean retreat() {
		this.slot--;
		return settleBackward();
	}

	Object key() {
		return this.window.keyAt(this.slot);
	}

	Object value() {
		return this.window.valueAt(this.slot);
	}

	private boolean settleForward() {
		while (this.slot >= this.window.size()) {
			var highFence = this.window.highFence();
			if (highFence == Node.NO_FENCE) {
				return false;
			}
			this.source.loadLeaf(this.window, highFence, Routing.COVERING);
			this.slot = Relation.CEILING.slotIn(this.window, this.source.order(), highFence);
		}
		return true;
	}

	private boolean settleBackward() {
		while (this.slot < 0) {
			var lowFence = this.window.lowFence();
			if (lowFence == Node.NO_FENCE) {
				return false;
			}
			this.source.loadLeaf(this.window, lowFence, Routing.BELOW);
			this.slot = Relation.LOWER.slotIn(this.window, this.source.order(), lowFence);
		}
		return true;
	}

}
