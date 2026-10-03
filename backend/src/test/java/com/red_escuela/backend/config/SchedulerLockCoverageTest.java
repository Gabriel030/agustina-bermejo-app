package com.red_escuela.backend.config;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerLockCoverageTest {

    private static final Set<String> PER_INSTANCE_JOBS = Set.of(
            "com.red_escuela.backend.identity.infrastructure.LoginRateLimiter#evictIdleClients");

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
