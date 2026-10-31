package io.github.omarmahamid.bbtree;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.SortedSet;

public class BBTreeSet<E> extends KeySetView<E> implements Cloneable {

	public BBTreeSet() {
		this(new Tree(new KeyOrder(null, null), NodeConfig.defaults(), false));
	}

	public BBTreeSet(Comparator<? super E> comparator) {
		this(new Tree(new KeyOrder(comparator, null), NodeConfig.defaults(), false));
	}

	public BBTreeSet(Collection<? extends E> elements) {
		this(Tree.withElements(new KeyOrder(null, null), elements));
	}

	public BBTreeSet(SortedSet<E> elements) {
		this(Tree.withElements(new KeyOrder(elements.comparator(), null), elements));
	}

	BBTreeSet(Tree tree) {
		super(new NavigableRangeView<>(tree, KeyRange.UNBOUNDED, false), LeafNode.KEY_ONLY_VALUE);
	}

	private BBTreeSet(Builder<E> builder) {
		this(new Tree(new KeyOrder(builder.comparator, builder.inlineKey), builder.nodeConfig, false));
	}

	public static <E> Builder<E> builder() {
		return new Builder<>();
	}

	@Override
	public boolean addAll(Collection<? extends E> elements) {
		return tree().insertAll(elements);
	}

	@Override
	@SuppressWarnings("unchecked")
	public BBTreeSet<E> clone() {
		try {
			var copy = (BBTreeSet<E>) super.clone();
			copy.adoptMap(new NavigableRangeView<>(tree().copy(), KeyRange.UNBOUNDED, false));
			return copy;
		}
		catch (CloneNotSupportedException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private Tree tree() {
		return (Tree) map().store();
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

		public BBTreeSet<E> build() {
			return new BBTreeSet<>(this);
		}

	}

}
