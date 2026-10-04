package com.red_escuela.backend.billing.planning;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;

public record ChargePlanningFacts(
        Long enrollmentId,
        YearMonth month,
        LocalDate today,
        boolean academicallyActive,
        boolean suspendedOrWithdrawn,
        boolean planCompleted,
        boolean onlyFinalExamsLeft,
        YearMonth entryMonth,
        PaymentPlanCadence cadenceAtMonth,
        boolean hasAnyPaymentPlan,
        YearCycles cycles,
        Set<YearMonth> enrollmentFeePeriods,
        Set<YearMonth> recurringPeriods,
        Map<String, SlotWindow> windows) {
}
