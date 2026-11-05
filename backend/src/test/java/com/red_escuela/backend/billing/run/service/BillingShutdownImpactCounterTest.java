package com.red_escuela.backend.billing.run.service;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BillingShutdownImpactCounterTest {

    @Test
    void reportedScheduledJobsAreExactlyTheBillingClassesWithScheduledMethods() {
        JavaClasses billingClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.red_escuela.backend.billing");

        List<String> classesWithScheduledMethods = billingClasses.stream()
                .filter(javaClass -> javaClass.getMethods().stream().anyMatch(method -> method.isAnnotatedWith(Scheduled.class)))
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        assertThat(BillingShutdownImpactCounter.SCHEDULED_JOBS).containsExactlyElementsOf(classesWithScheduledMethods);
    }
}
