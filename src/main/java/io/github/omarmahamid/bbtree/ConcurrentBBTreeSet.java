package io.github.omarmahamid.bbtree;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;

public class ConcurrentBBTreeSet<E> extends KeySetView<E> implements Cloneable {

	public ConcurrentBBTreeSet() {
		this(new LatchedTree(new KeyOrder(null, null), NodeConfig.defaults(), false));
	}

	public ConcurrentBBTreeSet(Comparator<? super E> comparator) {
		this(new LatchedTree(new KeyOrder(comparator, null), NodeConfig.defaults(), false));
	}

	public ConcurrentBBTreeSet(Collection<? extends E> elements) {
		this(LatchedTree.withElements(new KeyOrder(null, null), elements));
	}

	public ConcurrentBBTreeSet(SortedSet<E> elements) {
		this(LatchedTree.withElements(new KeyOrder(elements.comparator(), null), elements));
	}

	ConcurrentBBTreeSet(LatchedTree tree) {
		super(new ConcurrentNavigableRangeView<>(tree, KeyRange.UNBOUNDED, false), LeafNode.KEY_ONLY_VALUE);
	}

	private ConcurrentBBTreeSet(Builder<E> builder) {
		this(new LatchedTree(new KeyOrder(builder.comparator, builder.inlineKey), builder.nodeConfig, false));
	}

	public static <E> Builder<E> builder() {
		return new Builder<>();
	}

	@Override
	@SuppressWarnings("unchecked")
	public ConcurrentBBTreeSet<E> clone() {
		try {
			var copy = (ConcurrentBBTreeSet<E>) super.clone();
			copy.adoptMap(new ConcurrentNavigableRangeView<>(latchedTree().copy(), KeyRange.UNBOUNDED, false));
			return copy;
		}
		catch (CloneNotSupportedException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private LatchedTree latchedTree() {
		return (LatchedTree) map().store();
	}

	public static final class Builder<E> {

		private Comparator<? super E> comparator;

		private InlineKey<? super E> inlineKey;

		private NodeConfig nodeConfig = NodeConfig.defaults();

		private Builder() {
		}

		public Builder<E> comparator(Comparator<? super E> comparator) {
			this.comparator = Objects.requireNonNull(comparator, "The comparator can not be null");
			return this;
		}

		public Builder<E> inlineKey(InlineKey<? super E> inlineKey) {
			this.inlineKey = Objects.requireNonNull(inlineKey, "The inline key can not be null");
			return this;
		}

		public Builder<E> nodeConfig(NodeConfig nodeConfig) {
			this.nodeConfig = Objects.requireNonNull(nodeConfig, "The node config can not be null");
			return this;
		}

		public ConcurrentBBTreeSet<E> build() {
			return new ConcurrentBBTreeSet<>(this);
		}

	}

}
