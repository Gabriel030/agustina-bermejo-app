package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.CourseInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningDecision;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.planning.policy.CourseInstallmentPlanningContext;
import com.red_escuela.backend.billing.planning.policy.EligibilityPlanningContext;
import com.red_escuela.backend.billing.planning.policy.PlanningPolicySetsConfiguration;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class CourseMonthlyInvoicePlanner implements MonthlyInvoicePlanner<CourseInvoicePlanningFacts> {
    private final PlanningPolicySetsConfiguration policySets;
    private final InvoicePlanningVerdictMapper verdictMapper = new InvoicePlanningVerdictMapper();
    private final CoursePlanningPeriods planningPeriods = new CoursePlanningPeriods();
    private final MonthlyInstallmentDueDateCalculator dueDateCalculator = new MonthlyInstallmentDueDateCalculator();

    public CourseMonthlyInvoicePlanner() {
        this(new PlanningPolicySetsConfiguration());
    }

    public CourseMonthlyInvoicePlanner(PlanningPolicySetsConfiguration policySets) {
        this.policySets = policySets;
    }

    public MonthlyInvoicePlan planInvoicesForMonth(CourseInvoicePlanningFacts facts) {
        PolicySet<EligibilityPlanningContext> eligibility = policySets.courseEligibility();
        return verdictMapper
                .mapFirstViolationToPlanningDecision(eligibility, eligibility.evaluateRulesUntilFirstViolation(EligibilityPlanningContext.ofCourse(facts)))
                .map(decision -> new MonthlyInvoicePlan(facts.enrollmentId(), facts.month(), List.of(), List.of(decision), false))
                .orElseGet(() -> planEligibleEnrollment(facts));
    }

    private MonthlyInvoicePlan planEligibleEnrollment(CourseInvoicePlanningFacts facts) {
        List<PlannedInvoice> charges = new ArrayList<>();
        List<InvoicePlanningDecision> decisions = new ArrayList<>();
        decisions.add(verdictMapper.mapEligibleToPlanningDecision(facts.cadence()));
        AtomicLong issued = new AtomicLong(facts.issuedInTerm());
        CourseInstallmentMode mode = planningPeriods.modeOf(facts);
        planningPeriods.periodsToPlan(facts).forEach(period -> planPeriod(facts, period, issued, mode, charges, decisions));
        return new MonthlyInvoicePlan(facts.enrollmentId(), facts.month(), charges, decisions, false);
    }

    private void planPeriod(CourseInvoicePlanningFacts facts, YearMonth period, AtomicLong issued, CourseInstallmentMode mode, List<PlannedInvoice> charges,
                            List<InvoicePlanningDecision> decisions) {
        CourseInstallmentPlanningContext context = new CourseInstallmentPlanningContext(facts, period, issued.get());
        PolicySet<CourseInstallmentPlanningContext> policySet = policySets.courseMonthlyInstallmentPlanning();
        verdictMapper.mapFirstViolationToPlanningDecision(policySet, policySet.evaluateRulesUntilFirstViolation(context))
                .ifPresentOrElse(decisions::add, () -> planIssuableInstallment(context, mode, issued, charges, decisions));
    }

    private void planIssuableInstallment(CourseInstallmentPlanningContext context, CourseInstallmentMode mode, AtomicLong issued, List<PlannedInvoice> charges,
                                         List<InvoicePlanningDecision> decisions) {
        charges.add(new PlannedInvoice(FeeType.MONTHLY_INSTALLMENT, context.period(),
                dueDateCalculator.calculateInstallmentDueDate(context.facts().today(), context.facts().recurringFeeDueDays()), context.facts().today(), null, null,
                "cuota " + (context.installmentsIssued() + 1) + " del curso", false, mode.upfront()));
        decisions.add(new InvoicePlanningDecision(5, mode.decisionCode(), "cuota " + context.period()));
        issued.incrementAndGet();
    }
}
