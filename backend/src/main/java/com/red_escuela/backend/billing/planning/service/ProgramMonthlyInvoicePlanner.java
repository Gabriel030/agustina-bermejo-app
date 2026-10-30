package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningDecision;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningScope;
import com.red_escuela.backend.billing.planning.domain.LectiveYearCycleResolution;
import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.planning.domain.PlanningSection;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.policy.EligibilityPlanningContext;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotPlanningContext;
import com.red_escuela.backend.billing.planning.policy.MonthlyInstallmentPlanningContext;
import com.red_escuela.backend.billing.planning.policy.PlanningPolicySetsConfiguration;
import com.red_escuela.backend.shared.domain.policy.PolicySet;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ProgramMonthlyInvoicePlanner implements MonthlyInvoicePlanner<ProgramInvoicePlanningFacts> {
    private final PlanningPolicySetsConfiguration policySets;
    private final InvoicePlanningVerdictMapper verdictMapper = new InvoicePlanningVerdictMapper();
    private final LectiveYearCycleResolver cycleResolver = new LectiveYearCycleResolver();
    private final EnrollmentFeeSlotCandidateSelector candidateSelector = new EnrollmentFeeSlotCandidateSelector();
    private final EnrollmentFeeChargeFactory chargeFactory = new EnrollmentFeeChargeFactory();
    private final MonthlyInstallmentDueDateCalculator dueDateCalculator = new MonthlyInstallmentDueDateCalculator();

    public ProgramMonthlyInvoicePlanner() {
        this(new PlanningPolicySetsConfiguration());
    }

    public ProgramMonthlyInvoicePlanner(PlanningPolicySetsConfiguration policySets) {
        this.policySets = policySets;
    }

    public MonthlyInvoicePlan planInvoicesForMonth(ProgramInvoicePlanningFacts facts) {
        return planInvoicesForMonth(facts, InvoicePlanningScope.ALL);
    }

    public MonthlyInvoicePlan planInvoicesForMonth(ProgramInvoicePlanningFacts facts, InvoicePlanningScope scope) {
        return planInvoicesForMonth(facts, scope, false);
    }

    public MonthlyInvoicePlan planInvoicesForMonth(ProgramInvoicePlanningFacts facts, InvoicePlanningScope scope, boolean catchUp) {
        PolicySet<EligibilityPlanningContext> eligibility = policySets.programEligibility();
        return verdictMapper
                .mapFirstViolationToPlanningDecision(eligibility, eligibility.evaluateRulesUntilFirstViolation(EligibilityPlanningContext.ofProgram(facts)))
                .map(decision -> new MonthlyInvoicePlan(facts.enrollmentId(), facts.month(), List.of(), List.of(decision), false))
                .orElseGet(() -> planEligibleEnrollment(facts, scope, catchUp));
    }

    private MonthlyInvoicePlan planEligibleEnrollment(ProgramInvoicePlanningFacts facts, InvoicePlanningScope scope, boolean catchUp) {
        LectiveYearCycleResolution resolution = cycleResolver.resolveLectiveYearCycle(facts);
        List<PlannedInvoice> charges = new ArrayList<>();
        List<InvoicePlanningDecision> decisions = new ArrayList<>();
        decisions.add(verdictMapper.mapEligibleToPlanningDecision(facts.cadenceAtMonth()));
        decisions.add(resolution.toDecision());
        Map<PlanningSection, Runnable> sections = new EnumMap<>(PlanningSection.class);
        sections.put(PlanningSection.ENROLLMENT_FEES, () -> planEnrollmentFees(facts, catchUp, charges, decisions));
        sections.put(PlanningSection.MONTHLY_INSTALLMENT, () -> planMonthlyInstallment(facts, resolution, charges, decisions));
        scope.sections().forEach(section -> sections.get(section).run());
        return new MonthlyInvoicePlan(facts.enrollmentId(), facts.month(), charges, decisions, resolution.notConfigured());
    }

    private void planEnrollmentFees(ProgramInvoicePlanningFacts facts, boolean catchUp, List<PlannedInvoice> charges, List<InvoicePlanningDecision> decisions) {
        candidateSelector.selectEnrollmentFeeSlots(facts, catchUp).forEach(slot -> planEnrollmentFee(slot, charges, decisions));
    }

    private void planEnrollmentFee(EnrollmentFeeSlotPlanningContext context, List<PlannedInvoice> charges, List<InvoicePlanningDecision> decisions) {
        PolicySet<EnrollmentFeeSlotPlanningContext> policySet = policySets.enrollmentFeePlanning();
        verdictMapper.mapFirstViolationToPlanningDecision(policySet, policySet.evaluateRulesUntilFirstViolation(context))
                .ifPresentOrElse(decisions::add, () -> planIssuableEnrollmentFee(context, charges, decisions));
    }

    private void planIssuableEnrollmentFee(EnrollmentFeeSlotPlanningContext context, List<PlannedInvoice> charges, List<InvoicePlanningDecision> decisions) {
        PlannedInvoice charge = chargeFactory.createEnrollmentFeeCharge(context);
        charges.add(charge);
        decisions.add(new InvoicePlanningDecision(4, "ISSUE_ENROLLMENT_FEE", context.slotLabel() + " período " + charge.period() + " vence " + charge.dueDate()));
    }

    private void planMonthlyInstallment(ProgramInvoicePlanningFacts facts, LectiveYearCycleResolution resolution, List<PlannedInvoice> charges,
                                        List<InvoicePlanningDecision> decisions) {
        MonthlyInstallmentPlanningContext context = new MonthlyInstallmentPlanningContext(facts, resolution);
        PolicySet<MonthlyInstallmentPlanningContext> policySet = policySets.monthlyInstallmentPlanning();
        verdictMapper.mapFirstViolationToPlanningDecision(policySet, policySet.evaluateRulesUntilFirstViolation(context))
                .ifPresentOrElse(decisions::add, () -> planIssuableInstallment(context, charges, decisions));
    }

    private void planIssuableInstallment(MonthlyInstallmentPlanningContext context, List<PlannedInvoice> charges, List<InvoicePlanningDecision> decisions) {
        long sequence = context.installmentsIssued() + 1;
        int total = context.terms().monthlyInstallmentsCount();
        charges.add(new PlannedInvoice(FeeType.MONTHLY_INSTALLMENT, context.period(),
                dueDateCalculator.calculateInstallmentDueDate(context.facts().issueDate(), context.terms().monthlyInstallmentDueDays()),
                context.facts().today(), context.installmentCycle().templateId(), null, "cuota " + sequence + " de " + total + " del año lectivo", false, false));
        decisions.add(new InvoicePlanningDecision(5, "ISSUE_INSTALLMENT", "cuota " + sequence + " de " + total));
    }
}
