package com.red_escuela.backend.shared.resilience;

import com.red_escuela.backend.shared.exception.CircuitOpenException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BulkheadTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private Bulkhead bulkhead(boolean enabled, int maxConcurrent, Duration maxWait) {
        return new Bulkhead("provider", new BulkheadSettings(enabled, maxConcurrent, maxWait), meterRegistry);
    }

    @Test
    void onlyMaxConcurrentCallsEnterAndTheRestAreRejected() throws Exception {
        Bulkhead bulkhead = bulkhead(true, 2, Duration.ZERO);
        CountDownLatch insideTwo = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger entered = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<String>> blocked = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                blocked.add(pool.submit(() -> bulkhead.execute(() -> {
                    entered.incrementAndGet();
                    insideTwo.countDown();
                    awaitQuietly(release);
                    return "ok";
                })));
            }
            assertThat(insideTwo.await(5, TimeUnit.SECONDS)).isTrue();

            List<Future<String>> rejected = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                rejected.add(pool.submit(() -> bulkhead.execute(() -> {
                    entered.incrementAndGet();
                    return "ok";
                })));
            }
            for (Future<String> future : rejected) {
                assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(CircuitOpenException.class);
            }
            assertThat(entered.get()).isEqualTo(2);
            assertThat(bulkhead.rejectedCount()).isEqualTo(4);
            assertThat(bulkhead.availablePermits()).isZero();

            release.countDown();
            for (Future<String> future : blocked) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
            }
            assertThat(bulkhead.availablePermits()).isEqualTo(2);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void permitIsReleasedWhenTheCallThrows() {
        Bulkhead bulkhead = bulkhead(true, 1, Duration.ZERO);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> bulkhead.execute(() -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);
        }

        assertThat(bulkhead.availablePermits()).isEqualTo(1);
        assertThat(bulkhead.execute(() -> "ok")).isEqualTo("ok");
        assertThat(bulkhead.rejectedCount()).isZero();
    }

    @Test
    void disabledBulkheadIsPassThrough() {
        Bulkhead bulkhead = bulkhead(false, 1, Duration.ZERO);

        String result = bulkhead.execute(() -> bulkhead.execute(() -> bulkhead.execute(() -> "ok")));

        assertThat(result).isEqualTo("ok");
        assertThat(bulkhead.availablePermits()).isEqualTo(1);
        assertThat(bulkhead.rejectedCount()).isZero();
    }

    @Test
    void waitsUpToMaxWaitForAPermitBeforeRejecting() throws Exception {
        Bulkhead bulkhead = bulkhead(true, 1, Duration.ofMillis(300));
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> holder = pool.submit(() -> bulkhead.execute(() -> {
                inside.countDown();
                awaitQuietly(release);
                return "ok";
            }));
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

            long start = System.nanoTime();
            assertThatThrownBy(() -> bulkhead.execute(() -> "late")).isInstanceOf(CircuitOpenException.class);
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(waitedMillis).isGreaterThanOrEqualTo(250);
            release.countDown();
            assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void aCallThatGetsAPermitWithinMaxWaitRuns() throws Exception {
        Bulkhead bulkhead = bulkhead(true, 1, Duration.ofSeconds(5));
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> holder = pool.submit(() -> bulkhead.execute(() -> {
                inside.countDown();
                awaitQuietly(release);
                return "first";
            }));
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> waiter = pool.submit(() -> bulkhead.execute(() -> "second"));

            release.countDown();

            assertThat(waiter.get(5, TimeUnit.SECONDS)).isEqualTo("second");
            assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(bulkhead.rejectedCount()).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void rejectionIsNotRecordedAsAProviderFailureByTheBreaker() {
        Bulkhead bulkhead = bulkhead(true, 1, Duration.ZERO);
        CircuitBreaker breaker = new CircuitBreaker("provider",
                new CircuitBreakerSettings(true, 2, 1, Duration.ofSeconds(30)), Clock.systemUTC(), meterRegistry);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> Bulkhead.executeProtected(bulkhead, breaker, () -> {
                inside.countDown();
                awaitQuietly(release);
                return "ok";
            }));
            awaitQuietly(inside);

            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> Bulkhead.executeProtected(bulkhead, breaker, () -> "x"))
                        .isInstanceOf(CircuitOpenException.class);
            }

            assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
            assertThat(breaker.rejectedCount()).isZero();
            assertThat(meterRegistry.get("resilience.circuitbreaker.calls")
                    .tag("name", "provider").tag("result", "failure").counter().count()).isZero();
            release.countDown();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void anInterruptedThreadIsRejectedKeepsItsInterruptFlagAndLeavesPermitsIntact() {
        Bulkhead bulkhead = bulkhead(true, 2, Duration.ofSeconds(5));
        AtomicInteger entered = new AtomicInteger();

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> bulkhead.execute(() -> entered.incrementAndGet()))
                    .isInstanceOf(CircuitOpenException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        assertThat(entered.get()).isZero();
        assertThat(bulkhead.availablePermits()).isEqualTo(2);
        assertThat(bulkhead.rejectedCount()).isEqualTo(1);
    }

    @Test
    void executeProtectedHonorsTheFailureClassifierOfTheBreaker() {
        Bulkhead bulkhead = bulkhead(true, 4, Duration.ZERO);
        CircuitBreaker breaker = new CircuitBreaker("provider",
                new CircuitBreakerSettings(true, 2, 1, Duration.ofSeconds(30)), Clock.systemUTC(), meterRegistry);

        assertThatThrownBy(() -> Bulkhead.executeProtected(bulkhead, breaker, () -> {
            throw new IllegalArgumentException("client error");
        }, failure -> !(failure instanceof IllegalArgumentException))).isInstanceOf(IllegalArgumentException.class);
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

        assertThatThrownBy(() -> Bulkhead.executeProtected(bulkhead, breaker, () -> {
            throw new IllegalStateException("provider down");
        }, failure -> !(failure instanceof IllegalArgumentException))).isInstanceOf(IllegalStateException.class);
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
        assertThat(bulkhead.availablePermits()).isEqualTo(4);
    }

    @Test
    void metricsExposeRejectionsAndAvailablePermitsByName() {
        Bulkhead bulkhead = bulkhead(true, 1, Duration.ZERO);

        bulkhead.execute(() -> {
            assertThat(meterRegistry.get("resilience.bulkhead.available").tag("name", "provider").gauge().value()).isZero();
            assertThatThrownBy(() -> bulkhead.execute(() -> "x")).isInstanceOf(CircuitOpenException.class);
            return null;
        });

        assertThat(meterRegistry.get("resilience.bulkhead.rejected").tag("name", "provider").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("resilience.bulkhead.available").tag("name", "provider").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThatThrownBy(() -> new BulkheadSettings(true, 0, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BulkheadSettings(true, 1, Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
