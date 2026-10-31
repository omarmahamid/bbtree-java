package io.github.omarmahamid.bbtree;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;

public class BBTreeMap<K, V> extends NavigableRangeView<K, V> implements Cloneable {

	public BBTreeMap() {
		this(new Tree(new KeyOrder(null, null), NodeConfig.defaults(), true));
	}

	public BBTreeMap(Comparator<? super K> comparator) {
		this(new Tree(new KeyOrder(comparator, null), NodeConfig.defaults(), true));
	}

	public BBTreeMap(Map<? extends K, ? extends V> entries) {
		this(Tree.withEntries(new KeyOrder(null, null), entries));
	}

	public BBTreeMap(SortedMap<K, ? extends V> entries) {
		this(Tree.withEntries(new KeyOrder(entries.comparator(), null), entries));
	}

	BBTreeMap(Tree tree) {
		super(tree, KeyRange.UNBOUNDED, false);
	}

	private BBTreeMap(Builder<K, V> builder) {
		this(new Tree(new KeyOrder(builder.comparator, builder.inlineKey), builder.nodeConfig, true));
	}

	public static <K, V> Builder<K, V> builder() {
		return new Builder<>();
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> entries) {
		tree().insertAll(entries);
	}

	@Override
	@SuppressWarnings("unchecked")
	public BBTreeMap<K, V> clone() {
		try {
			var copy = (BBTreeMap<K, V>) super.clone();
			copy.adoptStore(tree().copy());
			return copy;
		}
		catch (CloneNotSupportedException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private Tree tree() {
		return (Tree) store();
	}

	public static final class Builder<K, V> {

		private Comparator<? super K> comparator;

		private InlineKey<? super K> inlineKey;

		private NodeConfig nodeConfig = NodeConfig.defaults();

		private Builder() {
		}

		public Builder<K, V> comparator(Comparator<? super K> comparator) {
			this.comparator = Objects.requireNonNull(comparator, "The comparator can not be null");
			return this;
		}

		public Builder<K, V> inlineKey(InlineKey<? super K> inlineKey) {
			this.inlineKey = Objects.requireNonNull(inlineKey, "The inline key can not be null");
			return this;
		}

		public Builder<K, V> nodeConfig(NodeConfig nodeConfig) {
			this.nodeConfig = Objects.requireNonNull(nodeConfig, "The node config can not be null");
			return this;
		}

		public BBTreeMap<K, V> build() {
			return new BBTreeMap<>(this);
		}

	}

}
