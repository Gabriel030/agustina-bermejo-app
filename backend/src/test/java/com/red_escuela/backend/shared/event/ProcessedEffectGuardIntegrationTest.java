package com.red_escuela.backend.shared.event;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-178")
@Tag("D-183")
@Tag("D-184")
class ProcessedEffectGuardIntegrationTest extends AbstractIntegrationTest {

    record ProbeEvent(UUID eventId, String correlationId, Instant occurredAt) implements DeduplicableDomainEvent {
    }

    @Autowired
    private ProcessedEffectGuard guard;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ProbeEvent newEvent() {
        return new ProbeEvent(UUID.randomUUID(), "c", Instant.now());
    }

    @Test
    void theSameEventAndHandlerRunsTheEffectOnlyOnce() {
        ProbeEvent event = newEvent();
        AtomicInteger runs = new AtomicInteger();

        boolean first = guard.runEffectOnce(event, "handler", runs::incrementAndGet);
        boolean second = guard.runEffectOnce(event, "handler", runs::incrementAndGet);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(runs).hasValue(1);
    }

    @Test
    void differentHandlersOfTheSameEventEachRunOnce() {
        ProbeEvent event = newEvent();
        AtomicInteger runs = new AtomicInteger();

        guard.runEffectOnce(event, "handler-a", runs::incrementAndGet);
        guard.runEffectOnce(event, "handler-b", runs::incrementAndGet);

        assertThat(runs).hasValue(2);
    }

    @Test
    void aFailingEffectDoesNotConsumeTheClaimSoTheRetryRunsIt() {
        ProbeEvent event = newEvent();
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                guard.runEffectOnce(event, "handler", () -> {
                    runs.incrementAndGet();
                    throw new IllegalStateException("smtp down");
                }))).isInstanceOf(IllegalStateException.class);
        boolean retried = guard.runEffectOnce(event, "handler", runs::incrementAndGet);

        assertThat(retried).isTrue();
        assertThat(runs).hasValue(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from processed_effect where event_id = ?", Long.class, event.eventId())).isEqualTo(1);
    }

    @Test
    void anEventWithoutIdAlwaysRunsTheEffect() {
        ProbeEvent legacy = new ProbeEvent(null, "c", Instant.now());
        AtomicInteger runs = new AtomicInteger();

        guard.runEffectOnce(legacy, "handler", runs::incrementAndGet);
        guard.runEffectOnce(legacy, "handler", runs::incrementAndGet);

        assertThat(runs).hasValue(2);
    }

    @Test
    void twoConcurrentDeliveriesOfTheSameEventRunTheEffectOnce() throws Exception {
        ProbeEvent event = newEvent();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = executor.submit(() -> concurrentDelivery(event, runs, start));
            Future<?> b = executor.submit(() -> concurrentDelivery(event, runs, start));
            start.countDown();
            a.get();
            b.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(runs).hasValue(1);
    }

    private void concurrentDelivery(ProbeEvent event, AtomicInteger runs, CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        transactionTemplate.executeWithoutResult(status -> guard.runEffectOnce(event, "handler", () -> {
            runs.incrementAndGet();
            sleepBriefly();
        }));
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
