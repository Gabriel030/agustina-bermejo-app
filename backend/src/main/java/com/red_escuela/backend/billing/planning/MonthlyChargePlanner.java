package com.red_escuela.backend.billing.planning;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Component
public final class MonthlyChargePlanner {

    public ChargePlan plan(ChargePlanningFacts facts, ChargeScope scope) {
        List<PlanDecision> decisions = new ArrayList<>();
        List<PlannedCharge> charges = new ArrayList<>();
        YearMonth month = facts.month();

        if (!facts.academicallyActive()) {
            decisions.add(new PlanDecision(1, "NOT_ACTIVE", "la inscripción no está ENROLLED ni PAUSED"));
            return new ChargePlan(facts.enrollmentId(), month, charges, decisions, false);
        }
        if (facts.suspendedOrWithdrawn()) {
            decisions.add(new PlanDecision(1, "SUSPENDED_OR_WITHDRAWN", "suspendida o dada de baja por falta de pago"));
            return new ChargePlan(facts.enrollmentId(), month, charges, decisions, false);
        }
        if (facts.planCompleted()) {
            decisions.add(new PlanDecision(1, "PLAN_COMPLETED", "el plan de estudios está completo"));
            return new ChargePlan(facts.enrollmentId(), month, charges, decisions, false);
        }
        if (!facts.hasAnyPaymentPlan()) {
            decisions.add(new PlanDecision(1, "NO_PAYMENT_PLAN", "no eligió plan de pago"));
            return new ChargePlan(facts.enrollmentId(), month, charges, decisions, false);
        }
        decisions.add(new PlanDecision(1, "ELIGIBLE", "plan de pago " + facts.cadenceAtMonth()));

        Optional<YearCycle> cuotaCycle = facts.cycles().forCuota(month);
        Optional<YearCycle> covering = facts.cycles().covering(month);
        boolean notConfigured = cuotaCycle.isEmpty() && covering.isEmpty();
        if (notConfigured) {
            decisions.add(new PlanDecision(2, "YEAR_NOT_CONFIGURED", "la carrera no tiene año lectivo que cubra " + month));
        } else {
            YearCycle cycle = cuotaCycle.orElseGet(covering::orElseThrow);
            decisions.add(new PlanDecision(2, "CYCLE", "plantilla " + cycle.templateId() + " con " + cycle.slotCount() + " matrícula(s)"));
        }

        if (scope != ChargeScope.RECURRING_ONLY) {
            planEnrollmentFees(facts, charges, decisions);
        }
        if (scope != ChargeScope.ENROLLMENT_FEE_ONLY) {
            planRecurringFee(facts, cuotaCycle, covering.isPresent(), charges, decisions);
        }
        return new ChargePlan(facts.enrollmentId(), month, charges, decisions, notConfigured);
    }

    private void planEnrollmentFees(ChargePlanningFacts facts, List<PlannedCharge> charges, List<PlanDecision> decisions) {
        for (YearCycle cycle : facts.cycles().all()) {
            ProgramYearBillingTemplate template = cycle.template();
            for (int index = 1; index <= cycle.slotCount(); index++) {
                final int slot = index;
                String label = "plantilla " + cycle.templateId() + " matrícula " + slot;
                if (facts.onlyFinalExamsLeft() && !template.isRequireEnrollmentFeeForFinalExamOnlyStudents()) {
                    if (isDueNow(facts, cycle, slot)) {
                        decisions.add(new PlanDecision(6, "ONLY_FINAL_EXAMS", label + " no se emite: solo debe finales"));
                    }
                    continue;
                }
                if (facts.entryMonth() != null && cycle.slotTo(slot).isBefore(facts.entryMonth())) {
                    continue;
                }
                boolean issued = facts.enrollmentFeePeriods().stream().anyMatch(p -> cycle.slotContains(slot, p));
                if (issued) {
                    if (isDueNow(facts, cycle, slot)) {
                        decisions.add(new PlanDecision(7, "ALREADY_ISSUED", label));
                    }
                    continue;
                }
                if (!isDueNow(facts, cycle, slot)) {
                    continue;
                }
                PlannedCharge charge = chargeForSlot(facts, cycle, slot, "matrícula del ciclo " + cycle.termStart().getYear() + " (" + label + ")");
                charges.add(charge);
                decisions.add(new PlanDecision(4, "ISSUE_ENROLLMENT_FEE", label + " período " + charge.period() + " vence " + charge.dueDate()));
            }
        }
    }

    public static PlannedCharge chargeForSlot(ChargePlanningFacts facts, YearCycle cycle, int slot, String reason) {
        YearMonth period = cycle.slotPeriod(slot);
        SlotWindow window = facts.windows().get(SlotWindow.key(cycle.templateId(), slot));
        if (window != null) {
            return new PlannedCharge(ChargeKind.ENROLLMENT_FEE, period, window.closesOn(), facts.today(), cycle.templateId(), slot, reason);
        }
        LocalDate due = period.atDay(1);
        return new PlannedCharge(ChargeKind.ENROLLMENT_FEE, period, due, due, cycle.templateId(), slot, reason);
    }

    private boolean isDueNow(ChargePlanningFacts facts, YearCycle cycle, int slot) {
        SlotWindow window = facts.windows().get(SlotWindow.key(cycle.templateId(), slot));
        if (window == null) {
            return cycle.slotPeriod(slot).equals(facts.month());
        }
        LocalDate emission = window.opensOn().minusDays(cycle.template().getEnrollmentFeeLeadDays());
        return !YearMonth.from(emission).isAfter(facts.month()) && !facts.today().isAfter(window.closesOn());
    }

    private void planRecurringFee(ChargePlanningFacts facts, Optional<YearCycle> cuotaCycle, boolean hasCoveringCycle,
                                  List<PlannedCharge> charges, List<PlanDecision> decisions) {
        YearMonth month = facts.month();
        if (facts.cadenceAtMonth() != PaymentPlanCadence.MONTHLY) {
            decisions.add(new PlanDecision(5, "PLAN_NOT_MONTHLY", "el plan vigente es " + facts.cadenceAtMonth()));
            return;
        }
        if (cuotaCycle.isEmpty()) {
            decisions.add(new PlanDecision(5, hasCoveringCycle ? "OUTSIDE_TERM" : "YEAR_NOT_CONFIGURED", "sin cuota en " + month));
            return;
        }
        YearCycle cycle = cuotaCycle.get();
        long issuedInTerm = facts.recurringPeriods().stream().filter(cycle::termContains).count();
        if (issuedInTerm >= cycle.template().getRecurringFeeInvoicesCount()) {
            decisions.add(new PlanDecision(5, "INSTALLMENT_CAP_REACHED", issuedInTerm + " de " + cycle.template().getRecurringFeeInvoicesCount()));
            return;
        }
        if (facts.onlyFinalExamsLeft()) {
            decisions.add(new PlanDecision(6, "ONLY_FINAL_EXAMS", "no se emite cuota: solo debe finales"));
            return;
        }
        if (facts.recurringPeriods().contains(month)) {
            decisions.add(new PlanDecision(7, "ALREADY_ISSUED", "cuota " + month));
            return;
        }
        LocalDate due = month.atDay(1);
        charges.add(new PlannedCharge(ChargeKind.RECURRING_FEE, month, due, due, cycle.templateId(), null,
                "cuota " + (issuedInTerm + 1) + " de " + cycle.template().getRecurringFeeInvoicesCount() + " del año lectivo"));
        decisions.add(new PlanDecision(5, "ISSUE_INSTALLMENT", "cuota " + (issuedInTerm + 1) + " de " + cycle.template().getRecurringFeeInvoicesCount()));
    }
}
