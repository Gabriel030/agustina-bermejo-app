package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.CourseInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningDecision;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.invoice.domain.FeeType;

public final class CourseMonthlyInvoicePlanner implements MonthlyInvoicePlanner<CourseInvoicePlanningFacts> {

    public MonthlyInvoicePlan planInvoicesForMonth(CourseInvoicePlanningFacts facts) {
        List<InvoicePlanningDecision> decisions = new ArrayList<>();
        List<PlannedInvoice> charges = new ArrayList<>();
        if (!facts.enrolled()) {
            decisions.add(new InvoicePlanningDecision(1, "NOT_ACTIVE", "la inscripción al curso no está ENROLLED"));
            return result(facts, charges, decisions);
        }
        if (facts.suspendedOrWithdrawn()) {
            decisions.add(new InvoicePlanningDecision(1, "SUSPENDED_OR_WITHDRAWN", "suspendida o dada de baja por falta de pago"));
            return result(facts, charges, decisions);
        }
        if (!facts.hasPaymentPlan() || facts.cadence() == null) {
            decisions.add(new InvoicePlanningDecision(1, "NO_PAYMENT_PLAN", "no eligió plan de pago"));
            return result(facts, charges, decisions);
        }
        decisions.add(new InvoicePlanningDecision(1, "ELIGIBLE", "plan de pago " + facts.cadence()));
        long issued = facts.issuedInTerm();
        if (facts.cadence() == PaymentPlanCadence.MONTHLY) {
            planPeriod(facts, facts.month(), issued, false, charges, decisions);
        } else if (facts.upfrontFrom() != null && facts.upfrontScopeMonths() != null) {
            YearMonth last = facts.upfrontFrom().plusMonths(facts.upfrontScopeMonths() - 1L);
            for (YearMonth period = facts.upfrontFrom(); !period.isAfter(last); period = period.plusMonths(1)) {
                if (planPeriod(facts, period, issued, true, charges, decisions)) {
                    issued++;
                }
            }
        }
        return result(facts, charges, decisions);
    }

    private boolean planPeriod(CourseInvoicePlanningFacts facts, YearMonth period, long issued, boolean upfront, List<PlannedInvoice> charges, List<InvoicePlanningDecision> decisions) {
        if (facts.issuedPeriods().contains(period)) {
            decisions.add(new InvoicePlanningDecision(7, "ALREADY_ISSUED", "cuota " + period));
            return false;
        }
        if (!facts.termContains().test(period)) {
            decisions.add(new InvoicePlanningDecision(5, "OUTSIDE_TERM", "sin cuota en " + period));
            return false;
        }
        if (facts.durationMonths() != null && issued >= facts.durationMonths()) {
            decisions.add(new InvoicePlanningDecision(5, "INSTALLMENT_CAP_REACHED", issued + " de " + facts.durationMonths()));
            return false;
        }
        LocalDate due = facts.today().plusDays(facts.recurringFeeDueDays());
        charges.add(new PlannedInvoice(FeeType.MONTHLY_INSTALLMENT, period, due, facts.today(), null, null, "cuota " + (issued + 1) + " del curso", false, upfront));
        decisions.add(new InvoicePlanningDecision(5, upfront ? "ISSUE_UPFRONT_INSTALLMENT" : "ISSUE_INSTALLMENT", "cuota " + period));
        return true;
    }

    private MonthlyInvoicePlan result(CourseInvoicePlanningFacts facts, List<PlannedInvoice> charges, List<InvoicePlanningDecision> decisions) {
        return new MonthlyInvoicePlan(facts.enrollmentId(), facts.month(), charges, decisions, false);
    }
}
