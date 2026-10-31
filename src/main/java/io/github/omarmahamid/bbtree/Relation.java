package io.github.omarmahamid.bbtree;

enum Relation {

	CEILING,

	HIGHER,

	FLOOR,

	LOWER;

	boolean ascending() {
		return this == CEILING || this == HIGHER;
	}

	Relation mirrored() {
		return switch (this) {
			case CEILING -> FLOOR;
			case HIGHER -> LOWER;
			case FLOOR -> CEILING;
			case LOWER -> HIGHER;
		};
	}

	int slotIn(LeafWindow window, KeyOrder order, Object key) {
		var found = window.find(order, key);
		var insertion = -(found + 1);
		return switch (this) {
			case CEILING -> found >= 0 ? found : insertion;
			case HIGHER -> found >= 0 ? found + 1 : insertion;
			case FLOOR -> found >= 0 ? found : insertion - 1;
			case LOWER -> found >= 0 ? found - 1 : insertion - 1;
		};
	}

}
