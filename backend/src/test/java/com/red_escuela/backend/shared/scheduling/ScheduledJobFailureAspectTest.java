package com.red_escuela.backend.shared.scheduling;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledJobFailureAspectTest {

    private static final Instant NOW = Instant.parse("2026-11-05T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private FailingJob proxied(FailingJob target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new ScheduledJobFailureAspect(registry, CLOCK));
        return factory.getProxy();
    }

    @Test
    void aFailingScheduledMethodIsCountedPerJobAndCauseAndStillPropagates() {
        FailingJob job = proxied(new FailingJob());

        assertThatThrownBy(job::run).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(job::run).isInstanceOf(IllegalStateException.class);

        assertThat(registry.counter(ScheduledJobFailureCounter.METER_NAME, "job", "FailingJob", "cause", "IllegalStateException").count()).isEqualTo(2.0);
    }

    @Test
    void aSuccessfulScheduledMethodDoesNotCountAFailure() {
        FailingJob job = proxied(new FailingJob());

        job.succeed();

        assertThat(registry.find(ScheduledJobFailureCounter.METER_NAME).counters()).isEmpty();
    }

    @Test
    void aSuccessfulRunPublishesTheLastSuccessGaugeWithTheClockTime() {
        FailingJob job = proxied(new FailingJob());

        job.succeed();

        assertThat(registry.get(ScheduledJobRunRecorder.LAST_SUCCESS_METER).tag("job", "FailingJob").gauge().value())
                .isEqualTo((double) NOW.getEpochSecond());
    }

    @Test
    void aFailedRunDoesNotAdvanceTheLastSuccessGauge() {
        FailingJob job = proxied(new FailingJob());

        assertThatThrownBy(job::run).isInstanceOf(IllegalStateException.class);

        assertThat(registry.find(ScheduledJobRunRecorder.LAST_SUCCESS_METER).gauges()).isEmpty();
    }

    @Test
    void durationTimerIsTaggedByOutcome() {
        FailingJob job = proxied(new FailingJob());

        job.succeed();
        assertThatThrownBy(job::run).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get(ScheduledJobRunRecorder.DURATION_METER).tag("job", "FailingJob").tag("outcome", "success").timer().count()).isEqualTo(1L);
        assertThat(registry.get(ScheduledJobRunRecorder.DURATION_METER).tag("job", "FailingJob").tag("outcome", "failure").timer().count()).isEqualTo(1L);
    }

    @Test
    void aScheduledMethodWithoutALockIsLeftUntouchedSoPerInstanceBeansAreNotProxied() {
        FailingJob job = proxied(new FailingJob());

        assertThatThrownBy(job::withoutLock).isInstanceOf(IllegalStateException.class);

        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void aMethodWithoutScheduledIsNotCounted() {
        FailingJob job = proxied(new FailingJob());

        assertThatThrownBy(job::unscheduled).isInstanceOf(IllegalStateException.class);

        assertThat(registry.getMeters()).isEmpty();
    }

    public static class FailingJob {

        @Scheduled(fixedDelay = 1000)
        @SchedulerLock(name = "run", lockAtMostFor = "PT1M")
        public void run() {
            throw new IllegalStateException("boom");
        }

        @Scheduled(fixedDelay = 1000)
        @SchedulerLock(name = "succeed", lockAtMostFor = "PT1M")
        public void succeed() {
        }

        @Scheduled(fixedDelay = 1000)
        public void withoutLock() {
            throw new IllegalStateException("boom");
        }

        public void unscheduled() {
            throw new IllegalStateException("boom");
        }
    }
}
