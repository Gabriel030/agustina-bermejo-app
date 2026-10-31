package com.red_escuela.backend.billing.planning.domain;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record ProgramInvoicePlanningFacts(
        Long enrollmentId,
        YearMonth month,
        LocalDate today,
        boolean academicallyActive,
        boolean suspendedOrWithdrawn,
        boolean planCompleted,
        boolean paused,
        boolean onlyFinalExamsLeft,
        YearMonth entryMonth,
        PaymentPlanCoverage paymentPlan,
        boolean hasAnyPaymentPlan,
        YearCycles cycles,
        List<IssuedEnrollmentFee> enrollmentFees,
        Set<YearMonth> recurringPeriods,
        Map<String, SlotWindow> windows,
        Long entryTemplateId,
        Integer entrySlot,
        LocalDate cursadaStart,
        LocalDate issueDate,
        Set<YearMonth> annualUpfrontAwaitingPaymentPeriods) {

    public ProgramInvoicePlanningFacts {
        annualUpfrontAwaitingPaymentPeriods = annualUpfrontAwaitingPaymentPeriods == null ? Set.of() : Set.copyOf(annualUpfrontAwaitingPaymentPeriods);
    }

    public ProgramInvoicePlanningFacts(Long enrollmentId, YearMonth month, LocalDate today, boolean academicallyActive, boolean suspendedOrWithdrawn,
                                       boolean planCompleted, boolean paused, boolean onlyFinalExamsLeft, YearMonth entryMonth,
                                       PaymentPlanCoverage paymentPlan, boolean hasAnyPaymentPlan, YearCycles cycles, List<IssuedEnrollmentFee> enrollmentFees,
                                       Set<YearMonth> recurringPeriods, Map<String, SlotWindow> windows, Long entryTemplateId, Integer entrySlot,
                                       LocalDate cursadaStart, LocalDate issueDate) {
        this(enrollmentId, month, today, academicallyActive, suspendedOrWithdrawn, planCompleted, paused, onlyFinalExamsLeft, entryMonth, paymentPlan,
                hasAnyPaymentPlan, cycles, enrollmentFees, recurringPeriods, windows, entryTemplateId, entrySlot, cursadaStart, issueDate, Set.of());
    }

    public ProgramInvoicePlanningFacts(Long enrollmentId, YearMonth month, LocalDate today, boolean academicallyActive, boolean suspendedOrWithdrawn,
                                       boolean planCompleted, boolean paused, boolean onlyFinalExamsLeft, YearMonth entryMonth,
                                       PaymentPlanCoverage paymentPlan, boolean hasAnyPaymentPlan, YearCycles cycles, List<IssuedEnrollmentFee> enrollmentFees,
                                       Set<YearMonth> recurringPeriods, Map<String, SlotWindow> windows, Long entryTemplateId, Integer entrySlot,
                                       LocalDate cursadaStart) {
        this(enrollmentId, month, today, academicallyActive, suspendedOrWithdrawn, planCompleted, paused, onlyFinalExamsLeft, entryMonth, paymentPlan,
                hasAnyPaymentPlan, cycles, enrollmentFees, recurringPeriods, windows, entryTemplateId, entrySlot, cursadaStart, today);
    }

    public ProgramInvoicePlanningFacts(Long enrollmentId, YearMonth month, LocalDate today, boolean academicallyActive, boolean suspendedOrWithdrawn,
                                       boolean planCompleted, boolean paused, boolean onlyFinalExamsLeft, YearMonth entryMonth,
                                       PaymentPlanCadence cadence, boolean hasAnyPaymentPlan, YearCycles cycles, List<IssuedEnrollmentFee> enrollmentFees,
                                       Set<YearMonth> recurringPeriods, Map<String, SlotWindow> windows, Long entryTemplateId, Integer entrySlot,
                                       LocalDate cursadaStart) {
        this(enrollmentId, month, today, academicallyActive, suspendedOrWithdrawn, planCompleted, paused, onlyFinalExamsLeft, entryMonth,
                PaymentPlanCoverage.ofCadence(cadence), hasAnyPaymentPlan, cycles, enrollmentFees, recurringPeriods, windows, entryTemplateId, entrySlot,
                cursadaStart);
    }

    public boolean annualUpfrontAwaitsPaymentFor(YearMonth period) {
        return annualUpfrontAwaitingPaymentPeriods.contains(period);
    }

    public PaymentPlanCadence cadenceAtMonth() {
        return paymentPlan.effectiveCadenceForMonth(month);
    }

    public boolean isEntrySlot(YearCycle cycle, int slot) {
        return entryTemplateId != null && entrySlot != null && entryTemplateId.equals(cycle.templateId()) && entrySlot == slot;
    }

    public Optional<IssuedEnrollmentFee> feeOf(YearCycle cycle, int slot) {
        List<IssuedEnrollmentFee> covering = enrollmentFees.stream().filter(f -> !f.chargeKeyReleased() && f.covers(cycle, slot)).toList();
        return covering.stream().filter(IssuedEnrollmentFee::isSettled).findFirst()
                .or(() -> covering.stream().filter(f -> !f.isCancelled()).findFirst())
                .or(() -> covering.stream().findFirst());
    }
}
