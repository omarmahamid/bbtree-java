package io.github.omarmahamid.bbtree;

final class Backoff {

	private static final int SPIN_LIMIT = 6;

	private static final int YIELD_LIMIT = 10;

	private int step;

	void pause() {
		if (this.step <= SPIN_LIMIT) {
			for (var spin = 0; spin < 1 << this.step; spin++) {
				Thread.onSpinWait();
			}
		}
		else {
			Thread.yield();
		}
		if (this.step <= YIELD_LIMIT) {
			this.step++;
		}
	}

}
