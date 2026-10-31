package io.github.omarmahamid.bbtree.bench;

import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.github.omarmahamid.bbtree.ConcurrentBBTreeMap;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(value = 1, jvmArgs = { "-Xms6g", "-Xmx6g" })
@Threads(8)
public class ConcurrentBenchmark {

	private static final int PROBE_COUNT = 1 << 20;

	@Param({ "bbtree", "skiplist" })
	public String map;

	@Param({ "0", "10" })
	public int writePercent;

	@Param({ "1000000" })
	public int size;

	private ConcurrentNavigableMap<Object, Object> shared;

	private Object[] probes;

	@Setup(Level.Trial)
	public void fill() {
		var keys = KeyShape.LONG.distinctKeys(this.size, 42);
		this.shared = "bbtree".equals(this.map) ? new ConcurrentBBTreeMap<>() : new ConcurrentSkipListMap<>();
		for (var key : keys) {
			this.shared.put(key, key);
		}
		this.probes = KeyShape.LONG.probesOf(keys, PROBE_COUNT, 7);
	}

	@Benchmark
	public Object mixedOperation() {
		var random = ThreadLocalRandom.current();
		var probe = this.probes[random.nextInt(PROBE_COUNT)];
		if (random.nextInt(100) < this.writePercent) {
			return this.shared.put(probe, probe);
		}
		return this.shared.get(probe);
	}

}
