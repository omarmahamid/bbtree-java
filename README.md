# bbtree-java

A Java port of [bbtree](https://github.com/XiangpengHao/bbtree), Xiangpeng Hao's B+tree for Rust:
cache-conscious nodes, inline keys, fence-key prefix stripping, and a concurrent map built on
optimistic lock coupling.

| Class | Drop-in replacement for | Interfaces |
|---|---|---|
| `BBTreeMap<K, V>` | `java.util.TreeMap` | `NavigableMap`, `Cloneable`, `Serializable` |
| `BBTreeSet<E>` | `java.util.TreeSet` | `NavigableSet`, `Cloneable`, `Serializable` |
| `ConcurrentBBTreeMap<K, V>` | `java.util.concurrent.ConcurrentSkipListMap` | `ConcurrentNavigableMap`, `Cloneable`, `Serializable` |
| `ConcurrentBBTreeSet<E>` | `java.util.concurrent.ConcurrentSkipListSet` | `NavigableSet`, `Cloneable`, `Serializable` |

Java 21+, no runtime dependencies.

## Install

```xml
<dependency>
    <groupId>io.github.omarmahamid</groupId>
    <artifactId>bbtree</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle: `implementation("io.github.omarmahamid:bbtree:0.1.0")`

## Usage

```java
import io.github.omarmahamid.bbtree.BBTreeMap;
import io.github.omarmahamid.bbtree.ConcurrentBBTreeMap;

NavigableMap<String, Integer> sorted = new BBTreeMap<>();
sorted.put("two", 2);
sorted.put("one", 1);
sorted.firstKey();                       // "one"

var shared = new ConcurrentBBTreeMap<Long, Long>();
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> shared.put(1L, 10L));
    executor.submit(() -> shared.put(2L, 20L));
}
shared.read(2L, (key, value) -> value + 1);         // 21
shared.getMany(List.of(1L, 2L, 3L));                // [10, 20, null]
shared.merge(1L, 5L, Long::sum);                    // 15, atomically
```

Swap the import and the constructor: everything that compiles against `TreeMap`/`TreeSet` or
`ConcurrentSkipListMap`/`ConcurrentSkipListSet` keeps its behavior, including views, navigation,
fail-fast iterators (single-threaded), weakly consistent iterators (concurrent), the null policy,
`clone()` and serialization.

### Tuning

```java
var map = BBTreeMap.<byte[], String>builder()
    .comparator(Arrays::compareUnsigned)
    .inlineKey(InlineKey.forUnsignedByteArrays())
    .nodeConfig(new NodeConfig(64, 64))
    .build();
```

## The bbtree API on the concurrent map

`ConcurrentBBTreeMap` keeps the Rust map's closure API next to the `ConcurrentNavigableMap` one.

| Rust `bbtree::BBTreeMap` | `ConcurrentBBTreeMap` |
|---|---|
| `read(&k, f)` | `read(k, (key, value) -> ...)`, under the leaf's shared latch |
| `update(&k, f)` | `update(k, (key, value) -> ...)`, under the leaf's exclusive latch |
| `get_copied`, `get_cloned` | `get(k)` |
| `insert` | `put(k, v)` |
| `update_or_insert(k, v, f)` | `merge(k, v, f)`, once, under the exclusive latch |
| `get_or_insert(k, v, f)` | `getOrInsert(k, v, (key, value) -> ...)` |
| `remove`, `remove_entry` | `remove(k)` |
| `remove_if(&k, cond)` | `removeIf(k, (key, value) -> ...)`, returns the removed entry |
| `scan`, `scan_rev` | `scan(from, inclusive, to, inclusive, visitor)`, `scanDescending(...)`; a non-null visitor result stops the scan and is returned |
| `range_cloned` | `subMap(...)` views |
| `get_many_copied` | `getMany(keys)`, descending up to 8 lanes in lockstep |
| `read_many` | `readMany(keys, reader)` |
| `insert_many` | `putMany(entries)` |
| no `len` | `size()`, from a `LongAdder` |

The functions passed to `read`, `update`, `getOrInsert`, `removeIf`, `scan`, `compute*` and `merge`
run while their leaf is latched, as `ConcurrentHashMap` runs `compute` under a bin lock. They must
not call back into the same map, and they should be short.

## How it works

- **B+tree of array nodes.** A leaf holds parallel arrays of keys, values and inline words; an
  internal node holds separators, inline words and children. Nodes hold 64 entries by default
  (`NodeConfig`, 4 to 1023). A lookup on a million entries touches four nodes, where a red-black
  `TreeMap` follows about twenty pointers.
- **Inline keys.** `InlineKey` maps a key to 8-byte windows of an encoding that orders like the key.
  A node searches its `long[]` of windows with a branch-free count, and compares full keys only
  where windows tie. Keys whose whole encoding fits in one window (`Long`, `Integer`, `Double`,
  `Character`, ...) never tie, so their searches never call `compareTo`. Naturally ordered `String`, `Long`, `Integer`,
  `Short`, `Byte`, `Character`, `Double`, `Float` and `UUID` keys get inline keys automatically;
  other keys opt in through the builder, or are searched with their comparator.
- **Fence-key prefix stripping.** Every key in a node lies between the separators that bound it,
  so it shares the bytes those two fences share. A node's windows start past that prefix: deep in a
  tree of paths such as `/usr/share/doc/package-01234/README`, the windows hold the bytes that differ.
  The offset is refit whenever a split, merge or redistribution moves a node's fences.
- **Append-aware splits.** An insert past the last key of the rightmost node splits it unevenly, so
  ascending inserts leave full nodes behind.
- **Bulk loading.** `clone()`, deserialization, and constructing from or `putAll`/`addAll` of a
  `SortedMap`/`SortedSet` with the same comparator into an empty collection build the tree bottom-up
  in linear time.
- **Optimistic lock coupling** (`ConcurrentBBTreeMap`). Every node carries one 64-bit latch word:
  an exclusive bit, a 20-bit shared count and a 43-bit version. Readers descend without writing
  shared memory, validating each node's version after reading its child's, and restart on a
  mismatch. Writers take internal nodes by compare-and-swap from the version they read and never
  wait on them; they latch leaves, spinning then yielding. Inserts split full nodes top-down on the
  way to the leaf; removals merge or redistribute underfull nodes when the parent and sibling can be
  taken without waiting, and skip the merge otherwise. The JVM's garbage collector stands in for the
  Rust crate's type-stable pages: a reader holding a stale node keeps it alive until its version
  check sends it back to the root.
- **Batches.** `getMany` walks up to eight descents level by level, so their memory accesses
  overlap instead of waiting on one another.

## What is not ported

The Rust workspace also holds pieces that do not carry over to the JVM or were left out of this port:
the allocator trait and page layouts, explicit SIMD searches, the tree-wide shared prefix, narrower
internal windows and hash tags, file-backed maps (`bbtree::file`), `bbtree-store`, `bbtree-mmap`,
the visualizer, and the Verus proofs, Miri and Shuttle runs.

## Verification

`mvn verify` runs:

- **Differential tests** against `TreeMap`, `TreeSet` and `ConcurrentSkipListMap`: 40,000 random
  operations for each of 9 key kinds (dense and sparse longs, reverse-ordered integers, path strings,
  strings of `\u0000`/`￿`-heavy characters, strings with a declared inline key, UUIDs, doubles
  with `-0.0`/`NaN`/infinities, characters) and 4 node configurations, down to 4-entry nodes. Every
  ~1,000 operations the whole tree is checked: key order, fences, inline words against their keys,
  prefix offsets against fences, and uniform leaf depth.
- **Guava testlib conformance suites** (`NavigableMapTestSuiteBuilder`,
  `ConcurrentNavigableMapTestSuiteBuilder`, `NavigableSetTestSuiteBuilder`) for all four
  collections, with the features Guava declares for `TreeMap`, `TreeSet`, `ConcurrentSkipListMap`
  and `ConcurrentSkipListSet`, including a null-friendly comparator: 322,592 tests.
- **Concurrency stress tests**: interleaved writers, readers of stable keys during splits and
  merges, iterators and scans that must see strictly increasing keys, atomic `merge`/`compute`/
  `update`, racing `putIfAbsent`, concurrent appends and concurrent draining, each on 4-entry and
  default nodes, followed by a full invariant check.
- **Inline-key contract tests** for every built-in encoding.

## Benchmarks

`bench/` holds JMH benchmarks: `mvn install -DskipTests && mvn -f bench/pom.xml package && java -jar
bench/target/benchmarks.jar`.

One run on an Apple M5 (10 cores, 32 GB), JDK 25, 6 GB heap, 1 fork, 3 × 1 s measured iterations.
The machine had other work running (load average 7 to 12), so each cell gives the range of the three
iterations rather than a mean; the ranges are 10 to 20% wide.

| 1,000,000 entries, one thread | `BBTreeMap` | `TreeMap` |
|---|---|---|
| `get`, random present `Long` key | 139–170 ns | 607–677 ns |
| `get`, random present 16-char `String` key | 252–267 ns | 522–581 ns |
| 1M random `Long` inserts into an empty map | 184–201 ms | 421–433 ms |
| 1M random `String` inserts into an empty map | 176–188 ms | 555–587 ms |
| iterate all entries, `Long` keys | 14.1–14.6 ms | 75–79 ms |
| iterate all entries, `String` keys | 13.4–13.5 ms | 6.5–7.1 ms |

`TreeMap` wins the `String` iteration: after the setup's garbage collections had copied its entries
and keys next to each other in key order, its iteration read memory sequentially. Its `Long`
iteration ran without that layout.

| 1,000,000 `Long` entries, 8 threads | `ConcurrentBBTreeMap` | `ConcurrentSkipListMap` |
|---|---|---|
| `get` only | 19.6–21.5 ops/µs | 4.1–5.1 ops/µs |
| 90% `get`, 10% `put` | 15.0–20.4 ops/µs | 4.6–5.0 ops/µs |

| `ConcurrentBBTreeMap`, 1,000,000 entries, batches of 64 keys | `getMany` | 64 × `get` |
|---|---|---|
| `Long` keys, per key | 127–146 ns | 153–176 ns |
| `String` keys, per key | 197–200 ns | 268–278 ns |

## License

Like the original bbtree, licensed under either of the MIT License or the Apache License, Version
2.0, at your option; [LICENSE](LICENSE) holds both texts. The design and the data structure are
Xiangpeng Hao's; see the [bbtree repository](https://github.com/XiangpengHao/bbtree) and its
[design notes](https://github.com/XiangpengHao/bbtree/tree/main/design).
