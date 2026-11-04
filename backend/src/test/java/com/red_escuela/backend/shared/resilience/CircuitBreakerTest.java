package com.red_escuela.backend.shared.resilience;

import com.red_escuela.backend.shared.exception.CircuitOpenException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

class CircuitBreakerTest {

    private static final Duration WAIT_OPEN = Duration.ofSeconds(30);

    static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-04T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MovableClock clock;
    private SimpleMeterRegistry meters;
    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        meters = new SimpleMeterRegistry();
        breaker = new CircuitBreaker("gateway", new CircuitBreakerSettings(true, 5, 3, WAIT_OPEN), clock, meters);
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);
        }
    }

    private void succeedTimes(int times) {
        for (int i = 0; i < times; i++) {
            assertThat(breaker.execute(() -> "ok")).isEqualTo("ok");
        }
    }

    private double count(String metric, String... tags) {
        var search = meters.find(metric).tag("name", "gateway");
        for (int i = 0; i < tags.length; i += 2) {
            search = search.tag(tags[i], tags[i + 1]);
        }
        return search.counter().count();
    }

    @Test
    void staysClosedWhileFailuresAreBelowTheThreshold() {
        failTimes(2);
        succeedTimes(2);

        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void opensWhenFailuresInTheWindowReachTheThreshold() {
        failTimes(3);

        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void failuresThatSlideOutOfTheWindowNoLongerCount() {
        failTimes(2);
        succeedTimes(5);
        failTimes(2);

        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void rejectsCallsWithoutInvokingThemWhileOpen() {
        failTimes(3);
        AtomicInteger invocations = new AtomicInteger();

        assertThatThrownBy(() -> breaker.execute(invocations::incrementAndGet)).isInstanceOf(CircuitOpenException.class);

        assertThat(invocations).hasValue(0);
    }

    @Test
    void theRejectionCarriesTheRemainingOpenTimeAsRetryAfter() {
        failTimes(3);
        clock.advance(Duration.ofSeconds(10));

        assertThatThrownBy(() -> breaker.execute(() -> "x"))
                .isInstanceOfSatisfying(CircuitOpenException.class, open -> assertThat(open.retryAfter()).isEqualTo(Duration.ofSeconds(20)));
    }

    @Test
    void staysOpenUntilTheWaitElapses() {
        failTimes(3);
        clock.advance(WAIT_OPEN.minusMillis(1));

        assertThatThrownBy(() -> breaker.execute(() -> "x")).isInstanceOf(CircuitOpenException.class);
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void aSuccessfulProbeAfterTheWaitClosesTheCircuitAndResetsTheWindow() {
        failTimes(3);
        clock.advance(WAIT_OPEN);

        assertThat(breaker.execute(() -> "probe")).isEqualTo("probe");

        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
        failTimes(2);
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void aFailedProbeReopensTheCircuitForAFullNewWait() {
        failTimes(3);
        clock.advance(WAIT_OPEN);

        failTimes(1);

        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
        clock.advance(WAIT_OPEN.minusMillis(1));
        assertThatThrownBy(() -> breaker.execute(() -> "x")).isInstanceOf(CircuitOpenException.class);
        clock.advance(Duration.ofMillis(1));
        assertThat(breaker.execute(() -> "x")).isEqualTo("x");
    }

    @Test
    void exceptionsThatDoNotCountAsFailureNeverOpenTheCircuit() {
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new IllegalArgumentException("business");
            }, failure -> false)).isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void aDisabledBreakerNeverOpensNorRejects() {
        CircuitBreaker disabled = new CircuitBreaker("off", new CircuitBreakerSettings(false, 5, 3, WAIT_OPEN), clock, meters);

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> disabled.execute(() -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);
        }

        assertThat(disabled.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void onlyOneProbeRunsWhileTheCircuitIsHalfOpenUnderConcurrency() throws Exception {
        failTimes(3);
        clock.advance(WAIT_OPEN);
        int threads = 16;
        CountDownLatch probeStarted = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        return breaker.execute(() -> {
                            invocations.incrementAndGet();
                            probeStarted.countDown();
                            await(releaseProbe);
                            return "probe";
                        });
                    } catch (CircuitOpenException rejected) {
                        rejections.incrementAndGet();
                        return null;
                    }
                }));
            }
            go.countDown();
            assertThat(probeStarted.await(5, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (rejections.get() < threads - 1 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            releaseProbe.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            releaseProbe.countDown();
            pool.shutdownNow();
        }

        assertThat(invocations).hasValue(1);
        assertThat(rejections).hasValue(threads - 1);
        assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void recordsCallsByResultAndRejectionsInMetrics() {
        succeedTimes(2);
        failTimes(3);
        assertThatThrownBy(() -> breaker.execute(() -> "x")).isInstanceOf(CircuitOpenException.class);

        assertThat(count("resilience.circuitbreaker.calls", "result", "success")).isEqualTo(2);
        assertThat(count("resilience.circuitbreaker.calls", "result", "failure")).isEqualTo(3);
        assertThat(count("resilience.circuitbreaker.rejected")).isEqualTo(1);
    }

    @Test
    void exposesTheStateAsAGauge() {
        double closed = meters.get("resilience.circuitbreaker.state").tag("name", "gateway").gauge().value();
        failTimes(3);
        double open = meters.get("resilience.circuitbreaker.state").tag("name", "gateway").gauge().value();
        clock.advance(WAIT_OPEN);
        succeedTimes(1);
        double closedAgain = meters.get("resilience.circuitbreaker.state").tag("name", "gateway").gauge().value();

        assertThat(closed).isZero();
        assertThat(open).isEqualTo(1);
        assertThat(closedAgain).isZero();
    }

    @Test
    void settingsRejectAThresholdLargerThanTheWindow() {
        assertThatThrownBy(() -> new CircuitBreakerSettings(true, 3, 4, WAIT_OPEN)).isInstanceOf(IllegalArgumentException.class);
    }
}
