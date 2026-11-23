package com.red_escuela.backend.shared.resilience;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class BulkheadSaturator implements AutoCloseable {
    private final CountDownLatch release = new CountDownLatch(1);
    private final Thread holder;

    private BulkheadSaturator(Bulkhead bulkhead, int permits) {
        CountDownLatch acquired = new CountDownLatch(1);
        this.holder = new Thread(() -> bulkhead.execute(() -> occupy(bulkhead, permits - 1, acquired)));
        holder.start();
        await(acquired);
    }

    public static BulkheadSaturator occupying(Bulkhead bulkhead, int permits) {
        return new BulkheadSaturator(bulkhead, permits);
    }

    private Object occupy(Bulkhead bulkhead, int remaining, CountDownLatch acquired) {
        if (remaining > 0) {
            return bulkhead.execute(() -> occupy(bulkhead, remaining - 1, acquired));
        }
        acquired.countDown();
        await(release);
        return null;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @Override
    public void close() {
        release.countDown();
        try {
            holder.join(5000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
