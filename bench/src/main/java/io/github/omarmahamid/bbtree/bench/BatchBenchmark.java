package io.github.omarmahamid.bbtree.bench;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.omarmahamid.bbtree.ConcurrentBBTreeMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
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
public class BatchBenchmark {

	private static final int BATCH = 64;

	private static final int BATCH_COUNT = 1 << 14;

	@Param({ "LONG", "STRING" })
	public KeyShape keyShape;

	@Param({ "1000000" })
	public int size;

	private ConcurrentBBTreeMap<Object, Object> map;

	private List<List<Object>> batches;

	private int cursor;

	@Setup(Level.Trial)
	public void fill() {
		var keys = this.keyShape.distinctKeys(this.size, 42);
		this.map = new ConcurrentBBTreeMap<>();
		for (var key : keys) {
			this.map.put(key, key);
		}
		var probes = this.keyShape.probesOf(keys, BATCH * BATCH_COUNT, 7);
		this.batches = new ArrayList<>(BATCH_COUNT);
		for (var batch = 0; batch < BATCH_COUNT; batch++) {
			this.batches.add(Arrays.asList(Arrays.copyOfRange(probes, batch * BATCH, (batch + 1) * BATCH)));
		}
	}

	@Benchmark
	@OperationsPerInvocation(BATCH)
	public List<Object> getManyInLockstepLanes() {
		return this.map.getMany(nextBatch());
	}

	@Benchmark
	@OperationsPerInvocation(BATCH)
	public Object[] getOneAtATime() {
		var batch = nextBatch();
		var values = new Object[BATCH];
		for (var index = 0; index < BATCH; index++) {
			values[index] = this.map.get(batch.get(index));
		}
		return values;
	}

	private List<Object> nextBatch() {
		var batch = this.batches.get(this.cursor);
		this.cursor = (this.cursor + 1) & (BATCH_COUNT - 1);
		return batch;
	}

}
