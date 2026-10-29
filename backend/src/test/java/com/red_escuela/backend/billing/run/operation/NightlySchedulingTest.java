package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.service.EnrollmentBillingComplianceSweepService;
import com.red_escuela.backend.billing.service.SuspendedEnrollmentClosureService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-451")
@Tag("D-449")
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
}
