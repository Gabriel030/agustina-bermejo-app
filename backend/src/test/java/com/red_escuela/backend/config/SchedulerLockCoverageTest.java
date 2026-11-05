package com.red_escuela.backend.config;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerLockCoverageTest {

    private static final Set<String> PER_INSTANCE_JOBS = Set.of(
            "com.red_escuela.backend.identity.infrastructure.LoginRateLimiter#evictIdleClients",
            "com.red_escuela.backend.config.RequestRateLimitFilter#evictIdleClients");

    private static final Set<String> LOCK_SHORTER_THAN_PERIOD_ALLOWED = Set.of(
            "com.red_escuela.backend.billing.run.operation.FailedInvoiceRetryJob#run");
    private static final Duration FREQUENT_JOB_THRESHOLD = Duration.ofHours(1);

    private record ScheduledJob(String id, Class<?> type, Method method, Scheduled scheduled, SchedulerLock lock) {
    }

    private static List<ScheduledJob> lockedScheduledJobs() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<ScheduledJob> jobs = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.red_escuela.backend")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            for (Method method : type.getDeclaredMethods()) {
                Scheduled scheduled = method.getAnnotation(Scheduled.class);
                if (scheduled != null) {
                    jobs.add(new ScheduledJob(type.getName() + "#" + method.getName(), type, method, scheduled,
                            method.getAnnotation(SchedulerLock.class)));
                }
            }
        }
        return jobs;
    }

    static Duration minimumPeriodOf(Scheduled scheduled) {
        if (!scheduled.cron().isEmpty()) {
            CronExpression cron = CronExpression.parse(scheduled.cron());
            LocalDateTime previous = cron.next(LocalDateTime.of(2027, 1, 1, 0, 0));
            Duration minimum = null;
            for (int i = 0; i < 400 && previous != null; i++) {
                LocalDateTime next = cron.next(previous);
                if (next == null) {
                    break;
                }
                Duration gap = Duration.between(previous, next);
                minimum = minimum == null || gap.compareTo(minimum) < 0 ? gap : minimum;
                previous = next;
            }
            return minimum;
        }
        String text = !scheduled.fixedDelayString().isEmpty() ? scheduled.fixedDelayString() : scheduled.fixedRateString();
        if (!text.isEmpty()) {
            return text.startsWith("P") ? Duration.parse(text) : Duration.ofMillis(Long.parseLong(text));
        }
        long millis = scheduled.fixedDelay() >= 0 ? scheduled.fixedDelay() : scheduled.fixedRate();
        return Duration.ofMillis(millis);
    }

    static Duration lockAtMostForOf(SchedulerLock lock) {
        String text = lock.lockAtMostFor();
        return text.startsWith("P") ? Duration.parse(text) : Duration.ofMillis(Long.parseLong(text));
    }

    @Test
    void everyLockedJobDeclaresAnExplicitLockAtMostForConsistentWithItsExecutionPeriod() throws ClassNotFoundException {
        List<String> missing = new ArrayList<>();
        List<String> tooShort = new ArrayList<>();
        for (ScheduledJob job : lockedScheduledJobs()) {
            if (job.lock() == null) {
                continue;
            }
            if (job.lock().lockAtMostFor().isEmpty()) {
                missing.add(job.id());
                continue;
            }
            Duration period = minimumPeriodOf(job.scheduled());
            boolean frequent = period.compareTo(FREQUENT_JOB_THRESHOLD) <= 0;
            int comparison = lockAtMostForOf(job.lock()).compareTo(period);
            boolean invalid = frequent ? comparison <= 0 : comparison >= 0;
            if (invalid && !LOCK_SHORTER_THAN_PERIOD_ALLOWED.contains(job.id())) {
                tooShort.add(job.id() + " lockAtMostFor=" + job.lock().lockAtMostFor() + " period=" + period);
            }
        }
        assertThat(missing).isEmpty();
        assertThat(tooShort).isEmpty();
    }

    @Test
    void everyScheduledJobIsProxyableSoTheFailureAspectCountsItsFailures() throws ClassNotFoundException {
        List<String> notProxyable = new ArrayList<>();
        for (ScheduledJob job : lockedScheduledJobs()) {
            if (job.lock() == null) {
                continue;
            }
            int modifiers = job.method().getModifiers();
            boolean proxyable = !Modifier.isFinal(job.type().getModifiers())
                    && Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers) && !Modifier.isStatic(modifiers);
            if (!proxyable) {
                notProxyable.add(job.id());
            }
        }
        assertThat(notProxyable).isEmpty();
    }

    @Test
    void theMinimumPeriodIsReadFromCronAndFixedDelay() {
        assertThat(minimumPeriodOf(annotated("everyTenMinutes"))).isEqualTo(Duration.ofMinutes(10));
        assertThat(minimumPeriodOf(annotated("fixedFiveMinutes"))).isEqualTo(Duration.ofMinutes(5));
    }

    private static Scheduled annotated(String name) {
        try {
            return Sample.class.getDeclaredMethod(name).getAnnotation(Scheduled.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    static class Sample {
        @Scheduled(cron = "0 */10 9-11 * * MON-FRI")
        void everyTenMinutes() {
        }

        @Scheduled(fixedDelayString = "PT5M")
        void fixedFiveMinutes() {
        }
    }

    @Test
    void everyScheduledMethodDeclaresASchedulerLockWithAUniqueName() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<String> unlocked = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<String> duplicatedNames = new ArrayList<>();
        int scheduledMethods = 0;

        for (BeanDefinition definition : scanner.findCandidateComponents("com.red_escuela.backend")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Scheduled.class)) {
                    continue;
                }
                String id = type.getName() + "#" + method.getName();
                if (PER_INSTANCE_JOBS.contains(id)) {
                    continue;
                }
                scheduledMethods++;
                SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
                if (lock == null) {
                    unlocked.add(id);
                } else if (!names.add(lock.name())) {
                    duplicatedNames.add(lock.name());
                }
            }
        }

        assertThat(scheduledMethods).isGreaterThanOrEqualTo(5);
        assertThat(unlocked).isEmpty();
        assertThat(duplicatedNames).isEmpty();
    }
}
