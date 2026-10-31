package io.github.omarmahamid.bbtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class ConcurrentBBTreeMap<K, V> extends ConcurrentNavigableRangeView<K, V> implements Cloneable {

	public ConcurrentBBTreeMap() {
		this(new LatchedTree(new KeyOrder(null, null), NodeConfig.defaults(), true));
	}

	public ConcurrentBBTreeMap(Comparator<? super K> comparator) {
		this(new LatchedTree(new KeyOrder(comparator, null), NodeConfig.defaults(), true));
	}

	public ConcurrentBBTreeMap(Map<? extends K, ? extends V> entries) {
		this(LatchedTree.withEntries(new KeyOrder(null, null), entries));
	}

	public ConcurrentBBTreeMap(SortedMap<K, ? extends V> entries) {
		this(LatchedTree.withEntries(new KeyOrder(entries.comparator(), null), entries));
	}

	ConcurrentBBTreeMap(LatchedTree tree) {
		super(tree, KeyRange.UNBOUNDED, false);
	}

	private ConcurrentBBTreeMap(Builder<K, V> builder) {
		this(new LatchedTree(new KeyOrder(builder.comparator, builder.inlineKey), builder.nodeConfig, true));
	}

	public static <K, V> Builder<K, V> builder() {
		return new Builder<>();
	}

	@SuppressWarnings("unchecked")
	public <R> R read(K key, BiFunction<? super K, ? super V, ? extends R> reader) {
		Objects.requireNonNull(reader, "The reader can not be null");
		return latchedTree().read(key, (storedKey, storedValue) -> reader.apply((K) storedKey, (V) storedValue));
	}

	@SuppressWarnings("unchecked")
	public <R> R update(K key, BiFunction<? super K, ? super V, ? extends R> updater) {
		Objects.requireNonNull(updater, "The updater can not be null");
		return latchedTree().update(key, (storedKey, storedValue) -> updater.apply((K) storedKey, (V) storedValue));
	}

	@SuppressWarnings("unchecked")
	public <R> R getOrInsert(K key, V value, BiFunction<? super K, ? super V, ? extends R> reader) {
		Objects.requireNonNull(reader, "The reader can not be null");
		return latchedTree().getOrInsert(key, value,
				(storedKey, storedValue) -> reader.apply((K) storedKey, (V) storedValue));
	}

	@SuppressWarnings("unchecked")
	public Map.Entry<K, V> removeIf(K key, BiPredicate<? super K, ? super V> condition) {
		Objects.requireNonNull(condition, "The condition can not be null");
		var removed = latchedTree().removeIf(key,
				(storedKey, storedValue) -> condition.test((K) storedKey, (V) storedValue));
		return (Map.Entry<K, V>) (Map.Entry<?, ?>) removed;
	}

	public <R> R scan(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive,
			BiFunction<? super K, ? super V, ? extends R> visitor) {
		return scanInDirection(fromKey, fromInclusive, toKey, toInclusive, false, visitor);
	}

	public <R> R scanDescending(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive,
			BiFunction<? super K, ? super V, ? extends R> visitor) {
		return scanInDirection(fromKey, fromInclusive, toKey, toInclusive, true, visitor);
	}

	@SuppressWarnings("unchecked")
	public List<V> getMany(List<? extends K> keys) {
		Objects.requireNonNull(keys, "The keys can not be null");
		var found = latchedTree().getMany(keys.toArray());
		for (var index = 0; index < found.length; index++) {
			if (found[index] == OrderedStore.ABSENT) {
				found[index] = null;
			}
		}
		return (List<V>) Arrays.asList(found);
	}

	public <R> List<R> readMany(List<? extends K> keys, BiFunction<? super K, ? super V, ? extends R> reader) {
		Objects.requireNonNull(keys, "The keys can not be null");
		Objects.requireNonNull(reader, "The reader can not be null");
		var results = new ArrayList<R>(keys.size());
		for (var key : keys) {
			results.add(read(key, reader));
		}
		return results;
	}

	public List<V> putMany(List<? extends Map.Entry<? extends K, ? extends V>> entries) {
		Objects.requireNonNull(entries, "The entries can not be null");
		var previous = new ArrayList<V>(entries.size());
		for (var entry : entries) {
			previous.add(put(entry.getKey(), entry.getValue()));
		}
		return previous;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction, "The mapping function can not be null");
		var existing = get(key);
		if (existing != null) {
			return existing;
		}
		return (V) latchedTree().transform(key, true,
				(storedKey, current) -> current != null ? current : mappingFunction.apply((K) storedKey));
	}

	@Override
	@SuppressWarnings("unchecked")
	public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		Objects.requireNonNull(remappingFunction, "The remapping function can not be null");
		return (V) latchedTree().transform(key, false,
				(storedKey, current) -> current == null ? null : remappingFunction.apply((K) storedKey, (V) current));
	}

	@Override
	@SuppressWarnings("unchecked")
	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		Objects.requireNonNull(remappingFunction, "The remapping function can not be null");
		return (V) latchedTree().transform(key, true,
				(storedKey, current) -> remappingFunction.apply((K) storedKey, (V) current));
	}

	@Override
	@SuppressWarnings("unchecked")
	public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		Objects.requireNonNull(value, "A concurrent map can not hold a null value");
		Objects.requireNonNull(remappingFunction, "The remapping function can not be null");
		return (V) latchedTree().transform(key, true,
				(storedKey, current) -> current == null ? value : remappingFunction.apply((V) current, value));
	}

	@Override
	@SuppressWarnings("unchecked")
	public ConcurrentBBTreeMap<K, V> clone() {
		try {
			var copy = (ConcurrentBBTreeMap<K, V>) super.clone();
			copy.adoptStore(latchedTree().copy());
			return copy;
		}
		catch (CloneNotSupportedException impossible) {
			throw new AssertionError(impossible);
		}
	}

	@SuppressWarnings("unchecked")
	private <R> R scanInDirection(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive, boolean descending,
			BiFunction<? super K, ? super V, ? extends R> visitor) {
		Objects.requireNonNull(visitor, "The visitor can not be null");
		var order = latchedTree().order();
		if (fromKey != null && toKey != null && order.compare(fromKey, toKey) > 0) {
			throw new IllegalArgumentException("fromKey > toKey");
		}
		var range = new KeyRange(fromKey == null ? Node.NO_FENCE : fromKey, fromInclusive,
				toKey == null ? Node.NO_FENCE : toKey, toInclusive);
		return latchedTree().scan(range, descending,
				(storedKey, storedValue) -> visitor.apply((K) storedKey, (V) storedValue));
	}

	private LatchedTree latchedTree() {
		return (LatchedTree) store();
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

		public ConcurrentBBTreeMap<K, V> build() {
			return new ConcurrentBBTreeMap<>(this);
		}

	}

}
