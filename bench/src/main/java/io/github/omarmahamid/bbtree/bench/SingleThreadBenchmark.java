package io.github.omarmahamid.bbtree.bench;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import io.github.omarmahamid.bbtree.BBTreeMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgs = { "-Xms6g", "-Xmx6g" })
public class SingleThreadBenchmark {

	private static final int PROBE_COUNT = 1 << 20;

	@Param({ "bbtree", "treemap" })
	public String map;

	@Param({ "LONG", "STRING" })
	public KeyShape keyShape;

	@Param({ "1000000" })
	public int size;

	private NavigableMap<Object, Object> filled;

	private Object[] probes;

	private Object[] insertKeys;

	private int cursor;

	@Setup(Level.Trial)
	public void fill() {
		var keys = this.keyShape.distinctKeys(this.size, 42);
		this.filled = emptyMap();
		for (var key : keys) {
			this.filled.put(key, key);
		}
		this.probes = this.keyShape.probesOf(keys, PROBE_COUNT, 7);
		this.insertKeys = this.keyShape.distinctKeys(this.size, 99);
	}

	@Benchmark
	public Object getHit() {
		var probe = this.probes[this.cursor];
		this.cursor = (this.cursor + 1) & (PROBE_COUNT - 1);
		return this.filled.get(probe);
	}

	@Benchmark
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	public NavigableMap<Object, Object> insertMillionRandomKeys() {
		var target = emptyMap();
		for (var key : this.insertKeys) {
			target.put(key, key);
		}
		return target;
	}

	@Benchmark
	public long iterateAll() {
		var sum = 0L;
		for (var entry : this.filled.entrySet()) {
			sum += entry.getKey().hashCode();
		}
		return sum;
	}

	private NavigableMap<Object, Object> emptyMap() {
		return "bbtree".equals(this.map) ? new BBTreeMap<>() : new TreeMap<>();
	}

}
