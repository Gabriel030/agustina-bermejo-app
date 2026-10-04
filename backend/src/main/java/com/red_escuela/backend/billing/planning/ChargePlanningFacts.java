package com.red_escuela.backend.billing.planning;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record ChargePlanningFacts(
        Long enrollmentId,
        YearMonth month,
        LocalDate today,
        boolean academicallyActive,
        boolean suspendedOrWithdrawn,
        boolean planCompleted,
        boolean paused,
        boolean onlyFinalExamsLeft,
        YearMonth entryMonth,
        PaymentPlanCadence cadenceAtMonth,
        boolean hasAnyPaymentPlan,
        YearCycles cycles,
        List<IssuedEnrollmentFee> enrollmentFees,
        Set<YearMonth> recurringPeriods,
        Map<String, SlotWindow> windows) {

    public Optional<IssuedEnrollmentFee> feeOf(YearCycle cycle, int slot) {
        List<IssuedEnrollmentFee> covering = enrollmentFees.stream().filter(f -> f.covers(cycle, slot)).toList();
        return covering.stream().filter(IssuedEnrollmentFee::isSettled).findFirst()
                .or(() -> covering.stream().filter(f -> !f.isCancelled()).findFirst())
                .or(() -> covering.stream().findFirst());
    }
}
