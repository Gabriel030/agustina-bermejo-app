package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.service.CollectionReminderFactsLoader;
import com.red_escuela.backend.billing.service.MonthlyInstallmentIssuanceJob;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class CollectionReminderPlanningJobIT extends AbstractIntegrationTest {

    private static final String START_MESSAGE = "Planificación de recordatorios de cobranza iniciada";

    @Autowired
    private CollectionReminderPlanningJob job;
    @Autowired
    private LockProvider lockProvider;

    private static String cronOf(Class<?> type, String method) throws NoSuchMethodException {
        Method scheduled = type.getDeclaredMethod(method);
        return scheduled.getAnnotation(Scheduled.class).cron();
    }

    @Test
    @Tag("D-353")
    void theJobRunsWhenNobodyHoldsItsLock(CapturedOutput output) {
        job.run();

        assertThat(output.getOut()).contains(START_MESSAGE);
    }

    @Test
    @Tag("D-353")
    void theJobIsSkippedWhileAnotherInstanceHoldsItsLock(CapturedOutput output) {
        Optional<SimpleLock> heldByOtherInstance = lockProvider.lock(new LockConfiguration(Instant.now(), "CollectionReminderPlanningJob", Duration.ofMinutes(5), Duration.ZERO));
        assertThat(heldByOtherInstance).isPresent();
        try {
            job.run();
        } finally {
            heldByOtherInstance.get().unlock();
        }

        assertThat(output.getOut()).doesNotContain(START_MESSAGE);
    }

    @Test
    @Tag("D-353")
    void plannerRunsAfterTheMonthlyIssuanceOfEveryIssuanceDay() throws NoSuchMethodException {
        CronExpression issuance = CronExpression.parse(cronOf(MonthlyInstallmentIssuanceJob.class, "run"));
        CronExpression planning = CronExpression.parse(CollectionReminderPlanningJob.CRON);

        for (int day = 1; day <= 5; day++) {
            LocalDateTime midnight = LocalDate.of(2027, 3, day).atStartOfDay();
            LocalDateTime issuanceFires = issuance.next(midnight.minusSeconds(1));
            LocalDateTime planningFires = planning.next(midnight.minusSeconds(1));
            assertThat(planningFires).as("day " + day).isAfter(issuanceFires);
        }
    }

    @Test
    @Tag("D-353")
    void theCronMatchesThePreviousRunTimeUsedToRecoverLateIssuedInvoices() {
        LocalDateTime firesAt = CronExpression.parse(CollectionReminderPlanningJob.CRON).next(LocalDate.of(2027, 3, 1).atStartOfDay().minusSeconds(1));

        assertThat(firesAt.toLocalTime()).isEqualTo(CollectionReminderFactsLoader.PLANNING_RUN_TIME);
    }

    @Test
    @Tag("D-353")
    void theScheduledMethodDeclaresItsSchedulerLockWithTheJobName() throws NoSuchMethodException {
        SchedulerLock lock = CollectionReminderPlanningJob.class.getDeclaredMethod("run").getAnnotation(SchedulerLock.class);

        assertThat(lock.name()).isEqualTo("CollectionReminderPlanningJob");
        assertThat(cronOf(CollectionReminderPlanningJob.class, "run")).isEqualTo(CollectionReminderPlanningJob.CRON);
    }
}
