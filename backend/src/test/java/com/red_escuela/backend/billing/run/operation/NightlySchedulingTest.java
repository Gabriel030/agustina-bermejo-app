package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.service.CollectionReminderFactsLoader;
import com.red_escuela.backend.billing.service.EnrollmentBillingComplianceSweepService;
import com.red_escuela.backend.billing.service.SuspendedEnrollmentClosureService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-451")
@Tag("D-449")
@Tag("D-459")
class NightlySchedulingTest {

    private static List<String> scheduledCronsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(method -> method.getAnnotation(Scheduled.class)).filter(java.util.Objects::nonNull).map(Scheduled::cron).toList();
    }

    @Test
    void theComplianceSweepAndTheCycleClosureNoLongerHaveTheirOwnCron() {
        assertThat(scheduledCronsOf(EnrollmentBillingComplianceSweepService.class)).isEmpty();
        assertThat(scheduledCronsOf(SuspendedEnrollmentClosureService.class)).isEmpty();
    }

    @Test
    void theNightlyJobIsTheOnlyScheduledMethodOfTheNightPipelineSteps() {
        List<Class<?>> steps = List.of(EnrollmentBillingComplianceBillingRunStep.class, CollectionsSnapshotBillingRunStep.class, TermEndWithdrawalBillingRunStep.class,
                AccountCreditAllocationBillingRunStep.class);

        steps.forEach(step -> assertThat(Arrays.stream(step.getDeclaredMethods()).map(Method::getName).filter(name -> name.equals("run"))).isEmpty());
        assertThat(scheduledCronsOf(NightlyBillingJob.class)).containsExactly(NightlyBillingJob.CRON);
    }

    @Test
    @Tag("D-436")
    void theReminderPlanningReferenceTimeIsTheMomentTheNightStarts() {
        LocalDateTime nightStart = CronExpression.parse(NightlyBillingJob.CRON).next(LocalDateTime.of(2031, 10, 7, 0, 0));

        assertThat(CollectionReminderFactsLoader.PLANNING_RUN_TIME).isEqualTo(nightStart.toLocalTime());
    }

    @Test
    @Tag("D-449")
    void noCollectionsClassKeepsACronOfItsOwn() {
        assertThat(scheduledCronsOf(CollectionReminderPlanningBillingRunStep.class)).isEmpty();
        assertThat(scheduledCronsOf(com.red_escuela.backend.billing.collections.operation.CollectionsDailySnapshotStep.class)).isEmpty();
    }

    @Test
    void theWatchdogHasItsOwnCronAtSixInTheMorning() {
        assertThat(scheduledCronsOf(MissedBillingRunAlertJob.class)).containsExactly("0 0 6 * * *");
    }

    @Test
    void bothJobsFireEveryDayIncludingWeekendsAndHolidays() {
        CronExpression nightly = CronExpression.parse(NightlyBillingJob.CRON);
        CronExpression watchdog = CronExpression.parse(MissedBillingRunAlertJob.CRON);
        LocalDateTime fridayEvening = LocalDateTime.of(2031, 10, 10, 23, 0);
        LocalDateTime christmasEve = LocalDateTime.of(2031, 12, 24, 23, 0);

        assertThat(nightly.next(fridayEvening)).isEqualTo(LocalDateTime.of(2031, 10, 11, 3, 0));
        assertThat(nightly.next(fridayEvening.plusDays(1))).isEqualTo(LocalDateTime.of(2031, 10, 12, 3, 0));
        assertThat(watchdog.next(fridayEvening)).isEqualTo(LocalDateTime.of(2031, 10, 11, 6, 0));
        assertThat(watchdog.next(fridayEvening.plusDays(1))).isEqualTo(LocalDateTime.of(2031, 10, 12, 6, 0));
        assertThat(watchdog.next(christmasEve)).isEqualTo(LocalDateTime.of(2031, 12, 25, 6, 0));
    }
}
