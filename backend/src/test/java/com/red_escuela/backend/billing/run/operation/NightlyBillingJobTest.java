package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;
import com.red_escuela.backend.billing.run.service.NightlyBillingRunner;
import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NightlyBillingJobTest {
    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final JavaClasses MAIN_CLASSES = ImportedApplicationClasses.main();

    private final NightlyBillingRunner runner = mock(NightlyBillingRunner.class);

    private ZonedDateTime nextFireAfter(LocalDateTime localMoment) {
        return CronExpression.parse(NightlyBillingJob.CRON).next(localMoment.atZone(BUENOS_AIRES));
    }

    @Test
    @Tag("D-449")
    void theCronFiresAtThreeAmBuenosAiresAlsoAcrossMonthEndAndLeapDay() {
        assertThat(nextFireAfter(LocalDateTime.of(2027, 10, 7, 3, 0))).isEqualTo(ZonedDateTime.of(2027, 10, 8, 3, 0, 0, 0, BUENOS_AIRES));
        assertThat(nextFireAfter(LocalDateTime.of(2027, 10, 31, 12, 0))).isEqualTo(ZonedDateTime.of(2027, 11, 1, 3, 0, 0, 0, BUENOS_AIRES));
        assertThat(nextFireAfter(LocalDateTime.of(2028, 2, 28, 12, 0))).isEqualTo(ZonedDateTime.of(2028, 2, 29, 3, 0, 0, 0, BUENOS_AIRES));
        assertThat(nextFireAfter(LocalDateTime.of(2028, 2, 29, 12, 0))).isEqualTo(ZonedDateTime.of(2028, 3, 1, 3, 0, 0, 0, BUENOS_AIRES));
    }

    @Test
    @Tag("D-458")
    void theJobLockHoldsForTwoHoursAndIsNamedAfterTheJob() throws Exception {
        SchedulerLock lock = NightlyBillingJob.class.getMethod("run").getAnnotation(SchedulerLock.class);

        assertThat(lock.name()).isEqualTo("NightlyBillingJob");
        assertThat(lock.lockAtMostFor()).isEqualTo("PT2H");
    }

    @Test
    @Tag("D-449")
    void theBusinessDateIsTheClockDateInBuenosAiresNotTheUtcDate() {
        Clock lateEveningInBuenosAires = Clock.fixed(Instant.parse("2027-10-08T01:30:00Z"), ZoneOffset.ofHours(-3));

        new NightlyBillingJob(runner, lateEveningInBuenosAires).run();

        verify(runner).runNightlyBillingSteps(LocalDate.of(2027, 10, 7), NightlyBillingTrigger.SCHEDULED);
    }

    @Test
    @Tag("D-449")
    void theLegacyOverdueSweepHasNoSchedulerAndTheNightlyJobIsTheOnlyScheduledMethodOfTheRunPackage() {
        noMethods().that().areDeclaredInClassesThat().haveFullyQualifiedName("com.red_escuela.backend.billing.service.InvoiceOverdueSweepService")
                .should().beAnnotatedWith(Scheduled.class).check(MAIN_CLASSES);
        noMethods().that().areDeclaredInClassesThat().resideInAPackage("..billing.run..")
                .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(NightlyBillingJob.class.getName())
                .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(MissedBillingRunAlertJob.class.getName())
                .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(MonthlyInvoiceIssuanceJob.class.getName())
                .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(FailedInvoiceRetryJob.class.getName())
                .should().beAnnotatedWith(Scheduled.class).check(MAIN_CLASSES);
        assertThat(NightlyBillingJob.class.getMethods()).filteredOn(method -> method.isAnnotationPresent(Scheduled.class)).hasSize(1);
        assertThat(MissedBillingRunAlertJob.class.getMethods()).filteredOn(method -> method.isAnnotationPresent(Scheduled.class)).hasSize(1);
        assertThat(MonthlyInvoiceIssuanceJob.class.getMethods()).filteredOn(method -> method.isAnnotationPresent(Scheduled.class)).hasSize(1);
        assertThat(FailedInvoiceRetryJob.class.getMethods()).filteredOn(method -> method.isAnnotationPresent(Scheduled.class)).hasSize(1);
    }

    @Test
    @Tag("D-356")
    void nothingInTheRunPackageTouchesTheLegacyBatchExecutionLog() {
        noClassesInRunPackage().check(MAIN_CLASSES);
    }

    @Test
    @Tag("D-457")
    void theRunPackageLocksInvoicesOnlyThroughTheSubjectLockSequence() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses().that().resideInAPackage("..billing.run..")
                .should().dependOnClassesThat().haveSimpleName("InvoiceRepository")
                .orShould().dependOnClassesThat().haveSimpleName("InvoiceLockingService")
                .check(MAIN_CLASSES);
    }

    private static com.tngtech.archunit.lang.ArchRule noClassesInRunPackage() {
        return com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses().that().resideInAPackage("..billing.run..")
                .should().dependOnClassesThat().haveSimpleNameStartingWith("BatchExecutionLog");
    }
}
