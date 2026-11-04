package com.red_escuela.backend.shared.scheduling;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledJobFailureAspectTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private FailingJob proxied(FailingJob target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new ScheduledJobFailureAspect(registry));
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
    void aSuccessfulScheduledMethodIsNotCounted() {
        FailingJob job = proxied(new FailingJob());

        job.succeed();

        assertThat(registry.getMeters()).isEmpty();
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
