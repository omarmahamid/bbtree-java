package io.github.omarmahamid.bbtree;

import java.io.Serializable;

public record NodeConfig(int leafCapacity, int internalCapacity) implements Serializable {

	public static final int MINIMUM_CAPACITY = 4;

	public static final int MAXIMUM_CAPACITY = 1023;

	public static final int DEFAULT_LEAF_CAPACITY = 64;

	public static final int DEFAULT_INTERNAL_CAPACITY = 64;

	public NodeConfig {
		requireCapacityInBounds(leafCapacity, "leafCapacity");
		requireCapacityInBounds(internalCapacity, "internalCapacity");
	}

	public static NodeConfig defaults() {
		return new NodeConfig(DEFAULT_LEAF_CAPACITY, DEFAULT_INTERNAL_CAPACITY);
	}

	private static void requireCapacityInBounds(int capacity, String name) {
		if (capacity < MINIMUM_CAPACITY || capacity > MAXIMUM_CAPACITY) {
			throw new IllegalArgumentException(name + " must be between " + MINIMUM_CAPACITY + " and "
					+ MAXIMUM_CAPACITY + ", but was " + capacity);
		}
	}

}
