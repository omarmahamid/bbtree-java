package io.github.omarmahamid.bbtree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class BBTreeMapTest {

	@Test
	void sequentialInsertsFillLeavesInsteadOfLeavingThemHalfEmpty() {
		var map = BBTreeMap.<Long, Long>builder().nodeConfig(new NodeConfig(16, 16)).build();
		for (long key = 0; key < 16 * 64; key++) {
			map.put(key, key);
		}
		var tree = (Tree) map.store();
		assertThat(leafCount(tree.root())).isLessThanOrEqualTo(65);
		assertThat(TreeShape.verifiedEntryCount(tree)).isEqualTo(16 * 64);
	}

	@Test
	void serializationKeepsTheComparatorInlineKeyAndNodeConfig() throws Exception {
		var config = new NodeConfig(6, 5);
		var map = BBTreeMap.<String, Integer>builder()
			.comparator(Comparator.naturalOrder())
			.inlineKey(InlineKey.forStrings())
			.nodeConfig(config)
			.build();
		for (var index = 0; index < 1_000; index++) {
			map.put("key-" + index, index);
		}
		var copy = roundTrip(map);
		assertThat(copy).isInstanceOf(BBTreeMap.class).isEqualTo(map);
		var tree = (Tree) ((BBTreeMap<?, ?>) copy).store();
		assertThat(tree.nodeConfig()).isEqualTo(config);
		assertThat(tree.order().declaredInlineKey()).isSameAs(InlineKey.forStrings());
		assertThat(TreeShape.verifiedEntryCount(tree)).isEqualTo(1_000);
	}

	@Test
	void serializedSubMapsAndSetsComeBackAsStandaloneCollections() throws Exception {
		var map = new BBTreeMap<Integer, String>();
		var set = new BBTreeSet<Integer>();
		for (var index = 0; index < 100; index++) {
			map.put(index, "v" + index);
			set.add(index);
		}
		var view = map.subMap(10, true, 20, false).descendingMap();
		assertThat(roundTrip(view)).isEqualTo(view).isInstanceOf(BBTreeMap.class);
		assertThat(roundTrip(set)).isEqualTo(set).isInstanceOf(BBTreeSet.class);
		assertThat(roundTrip(set.headSet(30))).isEqualTo(set.headSet(30));
	}

	@Test
	void cloneSharesNoNodesWithTheOriginal() {
		var map = new BBTreeMap<Integer, Integer>();
		for (var index = 0; index < 1_000; index++) {
			map.put(index, index);
		}
		var copy = map.clone();
		copy.keySet().removeIf(key -> key % 2 == 0);
		assertThat(map).hasSize(1_000);
		assertThat(copy).hasSize(500);
		assertThat(copy.firstKey()).isEqualTo(1);
	}

	@Test
	void iteratorsFailFastWhenTheMapChangesUnderneathThem() {
		var map = new BBTreeMap<Integer, Integer>();
		for (var index = 0; index < 100; index++) {
			map.put(index, index);
		}
		var iterator = map.keySet().iterator();
		iterator.next();
		map.put(1_000, 0);
		assertThatThrownBy(iterator::next).isInstanceOf(ConcurrentModificationException.class);
	}

	@Test
	void entriesFromIteratorsWriteThroughToTheMap() {
		var map = new BBTreeMap<Integer, Integer>(new TreeMap<>(java.util.Map.of(1, 1, 2, 2, 3, 3)));
		for (var entry : map.entrySet()) {
			entry.setValue(entry.getValue() * 10);
		}
		assertThat(new ArrayList<>(map.values())).containsExactly(10, 20, 30);
	}

	@Test
	void naturallyOrderedMapsRejectNullKeysLikeTreeMap() {
		var map = new BBTreeMap<String, String>();
		assertThatThrownBy(() -> map.put(null, "v")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> map.get(null)).isInstanceOf(NullPointerException.class);
		map.put("k", null);
		assertThat(map).containsEntry("k", null);
	}

	@Test
	void nodeConfigRejectsCapacitiesOutsideItsBounds() {
		assertThatThrownBy(() -> new NodeConfig(3, 64)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("leafCapacity must be between 4 and 1023, but was 3");
		assertThatThrownBy(() -> new NodeConfig(64, 1024)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("internalCapacity must be between 4 and 1023, but was 1024");
	}

	@Test
	void buildersRejectNullSettings() {
		assertThatThrownBy(() -> BBTreeMap.builder().comparator(null)).isInstanceOf(NullPointerException.class)
			.hasMessage("The comparator can not be null");
		assertThatThrownBy(() -> BBTreeSet.builder().nodeConfig(null)).isInstanceOf(NullPointerException.class)
			.hasMessage("The node config can not be null");
	}

	private static int leafCount(Node node) {
		if (node instanceof InternalNode internal) {
			var count = 0;
			for (var child = 0; child <= internal.size(); child++) {
				count += leafCount(internal.childAt(child));
			}
			return count;
		}
		return 1;
	}

	private static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
		var bytes = new ByteArrayOutputStream();
		try (var output = new ObjectOutputStream(bytes)) {
			output.writeObject(value);
		}
		try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			return input.readObject();
		}
	}

}
