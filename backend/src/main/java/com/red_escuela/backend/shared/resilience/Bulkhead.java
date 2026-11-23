package com.red_escuela.backend.shared.resilience;

import com.red_escuela.backend.shared.exception.CircuitOpenException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Bulkhead {
    private static final String REJECTED_METRIC = "resilience.bulkhead.rejected";
    private static final String AVAILABLE_METRIC = "resilience.bulkhead.available";
    private static final Duration MIN_RETRY_AFTER = Duration.ofSeconds(1);

    private final String name;
    private final BulkheadSettings settings;
    private final Semaphore permits;
    private final Counter rejectedCalls;

    public Bulkhead(String name, BulkheadSettings settings, MeterRegistry meterRegistry) {
        this.name = name;
        this.settings = settings;
        this.permits = new Semaphore(settings.maxConcurrent());
        this.rejectedCalls = Counter.builder(REJECTED_METRIC).tag("name", name).register(meterRegistry);
        Gauge.builder(AVAILABLE_METRIC, permits, Semaphore::availablePermits).tag("name", name).register(meterRegistry);
    }

    public String name() {
        return name;
    }

    public long rejectedCount() {
        return (long) rejectedCalls.count();
    }

    public int availablePermits() {
        return permits.availablePermits();
    }

    public <T> T execute(Supplier<T> call) {
        if (!settings.enabled()) {
            return call.get();
        }
        acquirePermit();
        try {
            return call.get();
        } finally {
            permits.release();
        }
    }

    public static <T> T executeProtected(Bulkhead bulkhead, CircuitBreaker breaker, Supplier<T> call) {
        return bulkhead.execute(() -> breaker.execute(call));
    }

    public static <T> T executeProtected(Bulkhead bulkhead, CircuitBreaker breaker, Supplier<T> call,
                                         Predicate<Throwable> countsAsFailure) {
        return bulkhead.execute(() -> breaker.execute(call, countsAsFailure));
    }

    private void acquirePermit() {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(settings.maxWait().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            rejectedCalls.increment();
            Duration retryAfter = settings.maxWait().compareTo(MIN_RETRY_AFTER) < 0 ? MIN_RETRY_AFTER : settings.maxWait();
            throw new BulkheadFullException(name, retryAfter);
        }
    }

    private static final class BulkheadFullException extends CircuitOpenException {
        BulkheadFullException(String bulkheadName, Duration retryAfter) {
            super(bulkheadName, retryAfter);
        }
    }
}
