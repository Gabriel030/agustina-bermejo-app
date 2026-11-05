package com.red_escuela.backend.shared.scheduling;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledJobCoverageTest {

    private static final Set<String> PER_INSTANCE_SWEEPS = Set.of(
            "com.red_escuela.backend.config.RequestRateLimitFilter.evictIdleClients",
            "com.red_escuela.backend.identity.infrastructure.LoginRateLimiter.evictIdleClients");

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.red_escuela.backend");

    @Test
    void everyScheduledMethodAlsoDeclaresASchedulerLock() {
        List<String> withoutLock = classes.stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(Scheduled.class))
                .filter(method -> !method.isAnnotatedWith(SchedulerLock.class))
                .map(JavaMethod::getFullName)
                .map(name -> name.substring(0, name.indexOf('(')))
                .filter(name -> !PER_INSTANCE_SWEEPS.contains(name))
                .toList();

        assertThat(withoutLock).isEmpty();
    }

    @Test
    void theDeclaredExceptionsAreStillScheduledWithoutALock() {
        List<String> actual = classes.stream()
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(Scheduled.class))
                .filter(method -> !method.isAnnotatedWith(SchedulerLock.class))
                .map(JavaMethod::getFullName)
                .map(name -> name.substring(0, name.indexOf('(')))
                .toList();

        assertThat(actual).containsExactlyInAnyOrderElementsOf(PER_INSTANCE_SWEEPS);
    }
}
