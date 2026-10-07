package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningScope;
import com.red_escuela.backend.billing.planning.domain.IssuedEnrollmentFee;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningDecision;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.invoice.domain.FeeType;

public final class ProgramMonthlyInvoicePlanner implements MonthlyInvoicePlanner<ProgramInvoicePlanningFacts> {

    public MonthlyInvoicePlan planInvoicesForMonth(ProgramInvoicePlanningFacts facts) {
        return planInvoicesForMonth(facts, InvoicePlanningScope.ALL);
    }

    public MonthlyInvoicePlan planInvoicesForMonth(ProgramInvoicePlanningFacts facts, InvoicePlanningScope scope) {
        return planInvoicesForMonth(facts, scope, false);
    }

    public MonthlyInvoicePlan planInvoicesForMonth(ProgramInvoicePlanningFacts facts, InvoicePlanningScope scope, boolean catchUp) {
        List<InvoicePlanningDecision> decisions = new ArrayList<>();
        List<PlannedInvoice> charges = new ArrayList<>();
        YearMonth month = facts.month();

        if (!facts.academicallyActive()) {
            decisions.add(new InvoicePlanningDecision(1, "NOT_ACTIVE", "la inscripción no está ENROLLED ni PAUSED"));
            return new MonthlyInvoicePlan(facts.enrollmentId(), month, charges, decisions, false);
        }
        if (facts.suspendedOrWithdrawn()) {
            decisions.add(new InvoicePlanningDecision(1, "SUSPENDED_OR_WITHDRAWN", "suspendida o dada de baja por falta de pago"));
            return new MonthlyInvoicePlan(facts.enrollmentId(), month, charges, decisions, false);
        }
        if (facts.planCompleted()) {
            decisions.add(new InvoicePlanningDecision(1, "PLAN_COMPLETED", "el plan de estudios está completo"));
            return new MonthlyInvoicePlan(facts.enrollmentId(), month, charges, decisions, false);
        }
        if (!facts.hasAnyPaymentPlan()) {
            decisions.add(new InvoicePlanningDecision(1, "NO_PAYMENT_PLAN", "no eligió plan de pago"));
            return new MonthlyInvoicePlan(facts.enrollmentId(), month, charges, decisions, false);
        }
        decisions.add(new InvoicePlanningDecision(1, "ELIGIBLE", "plan de pago " + facts.cadenceAtMonth()));

        Optional<YearCycle> cuotaCycle = facts.cycles().forInstallment(month);
        Optional<YearCycle> covering = facts.cycles().covering(month);
        boolean notConfigured = !facts.cycles().isConfiguredFor(month);
        if (notConfigured) {
            decisions.add(new InvoicePlanningDecision(2, "YEAR_NOT_CONFIGURED", "la carrera no tiene año lectivo que cubra " + month));
        } else if (cuotaCycle.isEmpty() && covering.isEmpty()) {
            decisions.add(new InvoicePlanningDecision(2, "BETWEEN_YEARS", "entre dos años lectivos configurados"));
        } else {
            YearCycle cycle = cuotaCycle.orElseGet(covering::orElseThrow);
            decisions.add(new InvoicePlanningDecision(2, "CYCLE", "plantilla " + cycle.templateId() + " con " + cycle.slotCount() + " matrícula(s)"));
        }

        if (scope != InvoicePlanningScope.MONTHLY_INSTALLMENT_ONLY) {
            planEnrollmentFees(facts, charges, decisions, catchUp);
        }
        if (scope != InvoicePlanningScope.ENROLLMENT_FEE_ONLY) {
            planRecurringFee(facts, cuotaCycle, covering.isPresent(), charges, decisions);
        }
        return new MonthlyInvoicePlan(facts.enrollmentId(), month, charges, decisions, notConfigured);
    }

    private void planEnrollmentFees(ProgramInvoicePlanningFacts facts, List<PlannedInvoice> charges, List<InvoicePlanningDecision> decisions, boolean catchUp) {
        for (YearCycle cycle : facts.cycles().all()) {
            ProgramYearBillingTemplate template = cycle.template();
            for (int index = 1; index <= cycle.slotCount(); index++) {
                final int slot = index;
                String label = "plantilla " + cycle.templateId() + " matrícula " + slot;
                boolean entry = facts.isEntrySlot(cycle, slot);
                if (!isDueNow(facts, cycle, slot, catchUp) && !(catchUp && entry)) {
                    continue;
                }
                if (facts.entryMonth() != null && cycle.slotTo(slot).isBefore(facts.entryMonth())) {
                    continue;
                }
                if (facts.paused() && !template.isPaysWhilePaused()) {
                    decisions.add(new InvoicePlanningDecision(1, "PAUSED_NOT_BILLED", label + " no se emite: inscripción en pausa académica"));
                    continue;
                }
                if (facts.onlyFinalExamsLeft() && !template.isRequireEnrollmentFeeForFinalExamOnlyStudents()) {
                    decisions.add(new InvoicePlanningDecision(6, "ONLY_FINAL_EXAMS", label + " no se emite: solo debe finales"));
                    continue;
                }
                Optional<IssuedEnrollmentFee> existing = facts.feeOf(cycle, slot);
                if (existing.isPresent()) {
                    decisions.add(new InvoicePlanningDecision(7, existing.get().isCancelled() ? "FEE_CANCELLED" : "ALREADY_ISSUED", label));
                    continue;
                }
                PlannedInvoice charge = chargeForSlot(facts, cycle, slot, "matrícula del ciclo " + cycle.termStart().getYear() + " (" + label + ")", catchUp);
                charges.add(charge);
                decisions.add(new InvoicePlanningDecision(4, "ISSUE_ENROLLMENT_FEE", label + " período " + charge.period() + " vence " + charge.dueDate()));
            }
        }
    }

    public PlannedInvoice chargeForSlot(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot, String reason) {
        return chargeForSlot(facts, cycle, slot, reason, false);
    }

    public PlannedInvoice chargeForSlot(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot, String reason, boolean catchUp) {
        boolean entry = facts.isEntrySlot(cycle, slot);
        if (entry && facts.cursadaStart() != null) {
            LocalDate entryDue = facts.cursadaStart().isBefore(facts.today())
                    ? facts.today().plusDays(cycle.template().getLateEnrollmentFeeDueDays())
                    : facts.cursadaStart();
            return new PlannedInvoice(FeeType.ENROLLMENT_FEE, facts.entryMonth(), entryDue, facts.today(), cycle.templateId(), slot, reason, true, false);
        }
        YearMonth period = cycle.slotPeriod(slot);
        SlotWindow window = facts.windows().get(SlotWindow.key(cycle.templateId(), slot));
        LocalDate regularDue = window != null ? window.closesOn() : period.atDay(1);
        LocalDate due = regularDue.isBefore(facts.today())
                ? facts.today().plusDays(cycle.template().getLateEnrollmentFeeDueDays())
                : regularDue;
        return new PlannedInvoice(FeeType.ENROLLMENT_FEE, period, due, facts.today(), cycle.templateId(), slot, reason, entry, false);
    }

    public YearMonth emissionMonth(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot) {
        SlotWindow window = facts.windows().get(SlotWindow.key(cycle.templateId(), slot));
        if (window == null) {
            return cycle.slotPeriod(slot);
        }
        return YearMonth.from(window.opensOn()).minusMonths(cycle.template().getEnrollmentFeeLeadMonths());
    }

    public boolean isDueNow(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot, boolean catchUp) {
        if (!catchUp) {
            SlotWindow window = facts.windows().get(SlotWindow.key(cycle.templateId(), slot));
            if (window == null) {
                return cycle.slotPeriod(slot).equals(facts.month());
            }
            return !emissionMonth(facts, cycle, slot).isAfter(facts.month()) && !facts.today().isAfter(window.closesOn());
        }
        return !emissionMonth(facts, cycle, slot).isAfter(facts.month()) && !cycle.slotTo(slot).isBefore(facts.month());
    }

    private void planRecurringFee(ProgramInvoicePlanningFacts facts, Optional<YearCycle> cuotaCycle, boolean hasCoveringCycle,
                                  List<PlannedInvoice> charges, List<InvoicePlanningDecision> decisions) {
        YearMonth month = facts.month();
        if (facts.cadenceAtMonth() == PaymentPlanCadence.ANNUAL_UPFRONT) {
            planUpfront(facts, charges, decisions);
            return;
        }
        if (facts.entryMonth() != null && month.isBefore(facts.entryMonth())) {
            decisions.add(new InvoicePlanningDecision(2, "BEFORE_ENTRY", "la cursada del alumno empieza en " + facts.entryMonth()));
            return;
        }
        if (facts.cadenceAtMonth() != PaymentPlanCadence.MONTHLY) {
            decisions.add(new InvoicePlanningDecision(5, "PLAN_NOT_MONTHLY", "el plan vigente es " + facts.cadenceAtMonth()));
            return;
        }
        if (cuotaCycle.isEmpty()) {
            decisions.add(new InvoicePlanningDecision(5, hasCoveringCycle ? "OUTSIDE_TERM" : "YEAR_NOT_CONFIGURED", "sin cuota en " + month));
            return;
        }
        YearCycle cycle = cuotaCycle.get();
        if (facts.paused() && !cycle.template().isPaysWhilePaused()) {
            decisions.add(new InvoicePlanningDecision(1, "PAUSED_NOT_BILLED", "no se emite cuota: inscripción en pausa académica"));
            return;
        }
        long issuedInTerm = facts.recurringPeriods().stream().filter(cycle::installmentRangeContains).count();
        if (issuedInTerm >= cycle.template().getRecurringFeeInvoicesCount()) {
            decisions.add(new InvoicePlanningDecision(5, "INSTALLMENT_CAP_REACHED", issuedInTerm + " de " + cycle.template().getRecurringFeeInvoicesCount()));
            return;
        }
        if (facts.onlyFinalExamsLeft()) {
            decisions.add(new InvoicePlanningDecision(6, "ONLY_FINAL_EXAMS", "no se emite cuota: solo debe finales"));
            return;
        }
        if (facts.recurringPeriods().contains(month)) {
            decisions.add(new InvoicePlanningDecision(7, "ALREADY_ISSUED", "cuota " + month));
            return;
        }
        LocalDate due = facts.today().plusDays(cycle.template().getRecurringFeeDueDays());
        charges.add(new PlannedInvoice(FeeType.MONTHLY_INSTALLMENT, month, due, facts.today(), cycle.templateId(), null,
                "cuota " + (issuedInTerm + 1) + " de " + cycle.template().getRecurringFeeInvoicesCount() + " del año lectivo", false, false));
        decisions.add(new InvoicePlanningDecision(5, "ISSUE_INSTALLMENT", "cuota " + (issuedInTerm + 1) + " de " + cycle.template().getRecurringFeeInvoicesCount()));
    }

    private void planUpfront(ProgramInvoicePlanningFacts facts, List<PlannedInvoice> charges, List<InvoicePlanningDecision> decisions) {
        if (facts.upfrontFrom() == null || facts.upfrontScopeMonths() == null) {
            decisions.add(new InvoicePlanningDecision(5, "UPFRONT_WITHOUT_SCOPE", "el plan anual no tiene alcance"));
            return;
        }
        if (facts.paused() || facts.onlyFinalExamsLeft()) {
            decisions.add(new InvoicePlanningDecision(5, facts.paused() ? "PAUSED_NOT_BILLED" : "ONLY_FINAL_EXAMS", "no se emiten cuotas del plan anual"));
            return;
        }
        YearMonth last = facts.upfrontFrom().plusMonths(facts.upfrontScopeMonths() - 1L);
        java.util.Map<Long, Long> issuedByTemplate = new java.util.HashMap<>();
        for (YearMonth period = facts.upfrontFrom(); !period.isAfter(last); period = period.plusMonths(1)) {
            Optional<YearCycle> cycle = facts.cycles().forInstallment(period);
            if (cycle.isEmpty() || facts.recurringPeriods().contains(period)) {
                continue;
            }
            YearCycle current = cycle.get();
            long issued = issuedByTemplate.computeIfAbsent(current.templateId(),
                    id -> facts.recurringPeriods().stream().filter(current::installmentRangeContains).count());
            if (issued >= current.template().getRecurringFeeInvoicesCount()) {
                decisions.add(new InvoicePlanningDecision(5, "INSTALLMENT_CAP_REACHED", "plan anual " + period));
                continue;
            }
            issuedByTemplate.put(current.templateId(), issued + 1);
            LocalDate due = facts.today().plusDays(current.template().getRecurringFeeDueDays());
            charges.add(new PlannedInvoice(FeeType.MONTHLY_INSTALLMENT, period, due, facts.today(), current.templateId(), null,
                    "cuota " + (issued + 1) + " del plan anual", false, true));
            decisions.add(new InvoicePlanningDecision(5, "ISSUE_UPFRONT_INSTALLMENT", "cuota del plan anual " + period));
        }
    }
}
