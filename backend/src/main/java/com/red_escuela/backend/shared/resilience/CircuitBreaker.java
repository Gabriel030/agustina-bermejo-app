package com.red_escuela.backend.shared.resilience;

import com.red_escuela.backend.shared.exception.CircuitOpenException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class CircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);
    private static final String CALLS_METRIC = "resilience.circuitbreaker.calls";
    private static final String REJECTED_METRIC = "resilience.circuitbreaker.rejected";
    private static final String STATE_METRIC = "resilience.circuitbreaker.state";

    private final String name;
    private final CircuitBreakerSettings settings;
    private final Clock clock;
    private final Counter successCalls;
    private final Counter failureCalls;
    private final Counter rejectedCalls;
    private final boolean[] outcomes;
    private final Object lock = new Object();

    private CircuitBreakerState state = CircuitBreakerState.CLOSED;
    private int recorded;
    private int nextSlot;
    private int failures;
    private Instant openedAt;
    private boolean probeInFlight;

    public CircuitBreaker(String name, CircuitBreakerSettings settings, Clock clock, MeterRegistry meterRegistry) {
        this.name = name;
        this.settings = settings;
        this.clock = clock;
        this.outcomes = new boolean[settings.window()];
        this.successCalls = Counter.builder(CALLS_METRIC).tag("name", name).tag("result", "success").register(meterRegistry);
        this.failureCalls = Counter.builder(CALLS_METRIC).tag("name", name).tag("result", "failure").register(meterRegistry);
        this.rejectedCalls = Counter.builder(REJECTED_METRIC).tag("name", name).register(meterRegistry);
        Gauge.builder(STATE_METRIC, this, breaker -> breaker.state().gaugeValue()).tag("name", name).register(meterRegistry);
    }

    public String name() {
        return name;
    }

    public long rejectedCount() {
        return (long) rejectedCalls.count();
    }

    public CircuitBreakerState state() {
        synchronized (lock) {
            return state;
        }
    }

    public <T> T execute(Supplier<T> call) {
        return execute(call, failure -> true);
    }

    public <T> T execute(Supplier<T> call, Predicate<Throwable> countsAsFailure) {
        if (!settings.enabled()) {
            return call.get();
        }
        boolean probe = admit();
        T result;
        try {
            result = call.get();
        } catch (RuntimeException | Error failure) {
            record(probe, countsAsFailure.test(failure));
            throw failure;
        }
        record(probe, false);
        return result;
    }

    private boolean admit() {
        synchronized (lock) {
            if (state == CircuitBreakerState.CLOSED) {
                return false;
            }
            Duration remaining = remainingOpenTime();
            if (state == CircuitBreakerState.OPEN && remaining.isZero()) {
                transitionTo(CircuitBreakerState.HALF_OPEN);
            }
            if (state == CircuitBreakerState.HALF_OPEN && !probeInFlight) {
                probeInFlight = true;
                return true;
            }
            rejectedCalls.increment();
            throw new CircuitOpenException(name, remaining);
        }
    }

    private void record(boolean probe, boolean failed) {
        synchronized (lock) {
            (failed ? failureCalls : successCalls).increment();
            if (probe) {
                probeInFlight = false;
                if (failed) {
                    openedAt = clock.instant();
                    transitionTo(CircuitBreakerState.OPEN);
                } else {
                    transitionTo(CircuitBreakerState.CLOSED);
                }
                return;
            }
            if (state != CircuitBreakerState.CLOSED) {
                return;
            }
            slide(failed);
            if (failures >= settings.failureThreshold()) {
                openedAt = clock.instant();
                transitionTo(CircuitBreakerState.OPEN);
            }
        }
    }

    private void slide(boolean failed) {
        if (recorded == outcomes.length && outcomes[nextSlot]) {
            failures--;
        }
        outcomes[nextSlot] = failed;
        if (failed) {
            failures++;
        }
        nextSlot = (nextSlot + 1) % outcomes.length;
        recorded = Math.min(recorded + 1, outcomes.length);
    }

    private Duration remainingOpenTime() {
        if (state != CircuitBreakerState.OPEN) {
            return Duration.ZERO;
        }
        Duration remaining = settings.waitOpen().minus(Duration.between(openedAt, clock.instant()));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private void transitionTo(CircuitBreakerState target) {
        CircuitBreakerState previous = state;
        state = target;
        if (target == CircuitBreakerState.CLOSED) {
            java.util.Arrays.fill(outcomes, false);
            recorded = 0;
            nextSlot = 0;
            failures = 0;
        }
        log.warn("Circuit breaker {} pasó de {} a {}", name, previous, target);
    }
}
