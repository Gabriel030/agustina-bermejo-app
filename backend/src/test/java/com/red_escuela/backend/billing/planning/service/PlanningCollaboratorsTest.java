package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.domain.CourseInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeObligationStatus;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningDecision;
import com.red_escuela.backend.billing.planning.domain.LectiveYearCycleResolution;
import com.red_escuela.backend.billing.planning.domain.ObligationInvoiceFacts;
import com.red_escuela.backend.billing.planning.domain.PaymentPlanCoverage;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.domain.YearCycles;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotDueThisMonthSpecification;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotPlanningContext;
import com.red_escuela.backend.billing.planning.policy.PlanningPolicySetsConfiguration;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningCollaboratorsTest {
    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private final ProgramYearBillingTemplate template = com.red_escuela.backend.billing.planning.policy.PlanningFixtures_.template();

    private static List<String> names(PolicySet<?> set) {
        return set.policies().stream().map(Policy::ruleName).toList();
    }

    @Test
    @Tag("D-050")
    void eachMomentDeclaresItsPoliciesInTheDecisionOrder() {
        PlanningPolicySetsConfiguration sets = new PlanningPolicySetsConfiguration();

        assertThat(sets.programEligibility().moment()).isEqualTo("PROGRAM_INVOICE_PLANNING_ELIGIBILITY");
        assertThat(names(sets.programEligibility())).containsExactly("AcademicallyActiveEnrollmentPolicy", "NotSuspendedOrWithdrawnForNonPaymentPolicy",
                "StudyPlanNotCompletedPolicy", "PaymentPlanChosenPolicy");
        assertThat(sets.courseEligibility().moment()).isEqualTo("COURSE_INVOICE_PLANNING_ELIGIBILITY");
        assertThat(names(sets.courseEligibility())).containsExactly("AcademicallyActiveEnrollmentPolicy", "NotSuspendedOrWithdrawnForNonPaymentPolicy",
                "PaymentPlanChosenPolicy");
        assertThat(sets.enrollmentFeePlanning().moment()).isEqualTo("ENROLLMENT_FEE_PLANNING");
        assertThat(names(sets.enrollmentFeePlanning())).containsExactly("BillingWhilePausedPolicy", "FinalExamsOnlyExemptionPolicy", "EnrollmentFeeSlotNotAlreadyIssuedPolicy");
        assertThat(sets.monthlyInstallmentPlanning().moment()).isEqualTo("MONTHLY_INSTALLMENT_PLANNING");
        assertThat(names(sets.monthlyInstallmentPlanning())).containsExactly("NotBeforeEntryMonthPolicy", "MonthlyCadencePolicy",
                "InstallmentWithinLectiveYearRangePolicy", "BillingWhilePausedPolicy", "InstallmentCapPolicy", "FinalExamsOnlyExemptionPolicy",
                "MonthlyInstallmentNotAlreadyIssuedPolicy");
        assertThat(sets.courseMonthlyInstallmentPlanning().moment()).isEqualTo("COURSE_MONTHLY_INSTALLMENT_PLANNING");
        assertThat(names(sets.courseMonthlyInstallmentPlanning())).containsExactly("MonthlyInstallmentNotAlreadyIssuedPolicy", "CourseInstallmentWithinTermPolicy",
                "InstallmentCapPolicy");
        assertThat(sets.enrollmentFeeRequirementApplicability().moment()).isEqualTo("ENROLLMENT_FEE_REQUIREMENT_APPLICABILITY");
        assertThat(names(sets.enrollmentFeeRequirementApplicability())).containsExactly("CertificateEnrollmentFeeExemptionPolicy", "EnrollmentFeeBlocksScopePolicy",
                "FinalExamsOnlyExemptionPolicy", "EnrollmentFeeSlotNotBeforeEntryPolicy", "EnrollmentFeeExemptionGrantedPolicy");
        assertThat(sets.enrollmentFeeRequirement().moment()).isEqualTo("ENROLLMENT_FEE_REQUIREMENT");
        assertThat(names(sets.enrollmentFeeRequirement())).containsExactly("EnrollmentFeeSettledPolicy");
    }

    @Test
    @Tag("D-050")
    void theMapperTurnsAViolationIntoTheQuestionCodeAndDetailOfItsPolicy() {
        InvoicePlanningVerdictMapper mapper = new InvoicePlanningVerdictMapper();
        PlanningPolicySetsConfiguration sets = new PlanningPolicySetsConfiguration();
        com.red_escuela.backend.billing.planning.policy.EligibilityPlanningContext suspended = new com.red_escuela.backend.billing.planning.policy.EligibilityPlanningContext(com.red_escuela.backend.billing.planning.policy.PlanningSubject.PROGRAM, true, true, false, true, PaymentPlanCadence.MONTHLY);
        com.red_escuela.backend.billing.planning.policy.EligibilityPlanningContext fine = new com.red_escuela.backend.billing.planning.policy.EligibilityPlanningContext(com.red_escuela.backend.billing.planning.policy.PlanningSubject.PROGRAM, true, false, false, true, PaymentPlanCadence.MONTHLY);

        assertThat(mapper.mapFirstViolationToPlanningDecision(sets.programEligibility(), sets.programEligibility().evaluateRulesUntilFirstViolation(suspended)))
                .contains(new InvoicePlanningDecision(1, "SUSPENDED_OR_WITHDRAWN", "suspendida o dada de baja por falta de pago"));
        assertThat(mapper.mapFirstViolationToPlanningDecision(sets.programEligibility(), sets.programEligibility().evaluateRulesUntilFirstViolation(fine))).isEmpty();
        assertThat(mapper.mapEligibleToPlanningDecision(PaymentPlanCadence.MONTHLY)).isEqualTo(new InvoicePlanningDecision(1, "ELIGIBLE", "plan de pago MONTHLY"));
        assertThat(mapper.mapViolationToPlanningDecision(new com.red_escuela.backend.billing.planning.policy.InstallmentCapPolicy<com.red_escuela.backend.billing.planning.policy.CourseInstallmentPlanningContext>(),
                PolicyVerdict.deny("InstallmentCapPolicy", "INSTALLMENT_CAP_REACHED", "10 de 10"))).isEqualTo(new InvoicePlanningDecision(5, "INSTALLMENT_CAP_REACHED", "10 de 10"));
    }

    private ProgramInvoicePlanningFacts facts(YearMonth month, LocalDate today, YearMonth entry, Map<String, SlotWindow> windows, Long entryTemplate, Integer entrySlot,
                                              LocalDate courseStart) {
        YearCycles cycles = new YearCycles(List.of(YearCycle.of(template, 2026)), null);
        return new ProgramInvoicePlanningFacts(1L, month, today, true, false, false, false, false, entry, PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY), true,
                cycles, List.of(), Set.of(), windows, entryTemplate, entrySlot, courseStart);
    }

    @Test
    @Tag("D-024")
    @Tag("D-025")
    @Tag("D-021")
    void dueDateStrategiesCloseAtTheWindowOrAfterTheLateDaysWhenTheDateAlreadyPassed() {
        YearCycle cycle = YearCycle.of(template, 2026);
        String key = SlotWindow.key(cycle.templateId(), 1);
        LocalDate today = LocalDate.of(2026, 3, 10);
        EntryEnrollmentFeeDueDateStrategy entry = new EntryEnrollmentFeeDueDateStrategy();
        ReenrollmentFeeDueDateStrategy reenrollment = new ReenrollmentFeeDueDateStrategy();

        EnrollmentFeeSlotPlanningContext future = EnrollmentFeeSlotPlanningContext.of(facts(MARCH, today, MARCH, Map.of(), cycle.templateId(), 1, LocalDate.of(2026, 3, 20)), cycle, 1, false);
        EnrollmentFeeSlotPlanningContext started = EnrollmentFeeSlotPlanningContext.of(facts(MARCH, today, MARCH, Map.of(), cycle.templateId(), 1, LocalDate.of(2026, 3, 1)), cycle, 1, false);
        EnrollmentFeeSlotPlanningContext noCohort = EnrollmentFeeSlotPlanningContext.of(facts(MARCH, today, MARCH, Map.of(), cycle.templateId(), 1, null), cycle, 1, false);
        assertThat(entry.appliesTo(future)).isTrue();
        assertThat(entry.appliesTo(noCohort)).isFalse();
        assertThat(entry.periodOf(future)).isEqualTo(MARCH);
        assertThat(entry.dueDateOf(future)).isEqualTo(LocalDate.of(2026, 3, 20));
        assertThat(entry.dueDateOf(started)).isEqualTo(today.plusDays(10));
        assertThat(reenrollment.appliesTo(noCohort)).isTrue();

        EnrollmentFeeSlotPlanningContext open = EnrollmentFeeSlotPlanningContext.of(facts(MARCH, today, null, Map.of(key, new SlotWindow(today.minusDays(5), today.plusDays(3))), null, null, null), cycle, 1, false);
        EnrollmentFeeSlotPlanningContext closesToday = EnrollmentFeeSlotPlanningContext.of(facts(MARCH, today, null, Map.of(key, new SlotWindow(today.minusDays(5), today)), null, null, null), cycle, 1, false);
        EnrollmentFeeSlotPlanningContext closed = EnrollmentFeeSlotPlanningContext.of(facts(MARCH, today, null, Map.of(key, new SlotWindow(today.minusDays(9), today.minusDays(1))), null, null, null), cycle, 1, false);
        EnrollmentFeeSlotPlanningContext noWindow = EnrollmentFeeSlotPlanningContext.of(facts(MARCH, today, null, Map.of(), null, null, null), cycle, 1, false);
        assertThat(reenrollment.dueDateOf(open)).isEqualTo(today.plusDays(3));
        assertThat(reenrollment.dueDateOf(closesToday)).isEqualTo(today);
        assertThat(reenrollment.dueDateOf(closed)).isEqualTo(today.plusDays(10));
        assertThat(reenrollment.dueDateOf(noWindow)).isEqualTo(today.plusDays(10));
        assertThat(reenrollment.periodOf(noWindow)).isEqualTo(cycle.slotPeriod(1));
        assertThat(new EnrollmentFeeChargeFactory().createEnrollmentFeeCharge(started).dueDate()).isEqualTo(today.plusDays(10));
        assertThat(new EnrollmentFeeChargeFactory().createEnrollmentFeeCharge(open).dueDate()).isEqualTo(today.plusDays(3));
    }

    @Test
    @Tag("D-022")
    @Tag("D-026")
    void aSlotIsCandidateInTheMonthBeforeItsWindowAndWhileItIsOpenAndInCatchUpUntilTheSlotEnds() {
        YearCycle cycle = YearCycle.of(template, 2026);
        String key = SlotWindow.key(cycle.templateId(), 1);
        LocalDate today = LocalDate.of(2026, 2, 10);
        YearMonth feb = YearMonth.of(2026, 2);
        Map<String, SlotWindow> opensNextMonth = Map.of(key, new SlotWindow(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 25)));
        Map<String, SlotWindow> opensInTwoMonths = Map.of(key, new SlotWindow(LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 25)));

        assertThat(EnrollmentFeeSlotDueThisMonthSpecification.isDueNow(facts(feb, today, null, opensNextMonth, null, null, null), cycle, 1, false)).isTrue();
        assertThat(EnrollmentFeeSlotDueThisMonthSpecification.isDueNow(facts(feb, today, null, opensInTwoMonths, null, null, null), cycle, 1, false)).isFalse();
        assertThat(EnrollmentFeeSlotDueThisMonthSpecification.isDueNow(facts(feb, today, null, Map.of(), null, null, null), cycle, 1, false)).isTrue();
        assertThat(EnrollmentFeeSlotDueThisMonthSpecification.isDueNow(facts(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 26), null, opensNextMonth, null, null, null), cycle, 1, false)).isFalse();
        assertThat(EnrollmentFeeSlotDueThisMonthSpecification.isDueNow(facts(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 25), null, opensNextMonth, null, null, null), cycle, 1, false)).isTrue();
        assertThat(EnrollmentFeeSlotDueThisMonthSpecification.isDueNow(facts(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 26), null, opensNextMonth, null, null, null), cycle, 1, true)).isTrue();
        assertThat(EnrollmentFeeSlotDueThisMonthSpecification.isDueNow(facts(YearMonth.of(2027, 1), LocalDate.of(2027, 1, 5), null, opensNextMonth, null, null, null), cycle, 1, true)).isFalse();
        assertThat(EnrollmentFeeSlotDueThisMonthSpecification.isDueNow(facts(feb, today, null, opensInTwoMonths, null, null, null), cycle, 1, true)).isFalse();
        assertThat(EnrollmentFeeSlotDueThisMonthSpecification.emissionMonth(facts(feb, today, null, opensNextMonth, null, null, null), cycle, 1)).isEqualTo(feb);
        template.setEnrollmentFeeLeadMonths(0);
        assertThat(EnrollmentFeeSlotDueThisMonthSpecification.emissionMonth(facts(feb, today, null, opensNextMonth, null, null, null), cycle, 1)).isEqualTo(MARCH);
    }

    @Test
    @Tag("D-022")
    void theSelectorKeepsOnlyTheSlotsDueThisMonthAndNotBeforeTheEntry() {
        EnrollmentFeeSlotCandidateSelector selector = new EnrollmentFeeSlotCandidateSelector();

        assertThat(selector.selectEnrollmentFeeSlots(facts(MARCH, MARCH.atDay(1), null, Map.of(), null, null, null), false)).extracting(EnrollmentFeeSlotPlanningContext::slot).containsExactly(1);
        assertThat(selector.selectEnrollmentFeeSlots(facts(YearMonth.of(2026, 5), MARCH.atDay(1), null, Map.of(), null, null, null), false)).isEmpty();
        assertThat(selector.selectEnrollmentFeeSlots(facts(MARCH, MARCH.atDay(1), YearMonth.of(2027, 1), Map.of(), null, null, null), false)).isEmpty();
    }

    @Test
    @Tag("D-001")
    @Tag("D-006")
    @Tag("D-008")
    void theResolverDistinguishesConfiguredYearsGapsAndUnconfiguredMonths() {
        LectiveYearCycleResolver resolver = new LectiveYearCycleResolver();

        assertThat(resolver.resolveLectiveYearCycle(facts(MARCH, MARCH.atDay(1), null, Map.of(), null, null, null))).isInstanceOf(LectiveYearCycleResolution.Configured.class);
        assertThat(resolver.resolveLectiveYearCycle(facts(YearMonth.of(2027, 5), MARCH.atDay(1), null, Map.of(), null, null, null))).isEqualTo(new LectiveYearCycleResolution.NotConfigured(YearMonth.of(2027, 5)));
        ProgramYearBillingTemplate later = com.red_escuela.backend.billing.planning.policy.PlanningFixtures_.template();
        later.setId(2L);
        later.getTerm().setYear(2028);
        later.getTerm().setStartDate(java.time.LocalDateTime.of(2028, 3, 1, 0, 0));
        later.getTerm().setEndDate(java.time.LocalDateTime.of(2028, 12, 31, 0, 0));
        ProgramInvoicePlanningFacts gap = new ProgramInvoicePlanningFacts(1L, YearMonth.of(2027, 5), LocalDate.of(2027, 5, 1), true, false, false, false, false, null,
                PaymentPlanCoverage.none(), true, new YearCycles(List.of(YearCycle.of(template, 2026), YearCycle.of(later, 2028)), null), List.of(), Set.of(), Map.of(), null, null, null);
        LectiveYearCycleResolution between = resolver.resolveLectiveYearCycle(gap);
        assertThat(between).isInstanceOf(LectiveYearCycleResolution.BetweenYears.class);
        assertThat(between.toDecision().code()).isEqualTo("BETWEEN_YEARS");
        assertThat(between.installmentCycle()).isEmpty();
        assertThat(between.hasCoveringCycle()).isFalse();
        assertThat(between.notConfigured()).isFalse();
        LectiveYearCycleResolution notConfigured = new LectiveYearCycleResolution.NotConfigured(YearMonth.of(2030, 1));
        assertThat(notConfigured.notConfigured()).isTrue();
        assertThat(notConfigured.hasCoveringCycle()).isFalse();
        assertThat(notConfigured.installmentCycle()).isEmpty();
        assertThat(notConfigured.toDecision().detail()).isEqualTo("la carrera no tiene año lectivo que cubra 2030-01");
        LectiveYearCycleResolution configured = resolver.resolveLectiveYearCycle(facts(MARCH, MARCH.atDay(1), null, Map.of(), null, null, null));
        assertThat(configured.toDecision().detail()).isEqualTo("plantilla 1 con 1 matrícula(s)");
        assertThat(configured.notConfigured()).isFalse();
        assertThat(configured.hasCoveringCycle()).isTrue();
    }

    @Test
    @Tag("D-039")
    void courseInstallmentPeriodsFollowTheCadenceAndTheAnnualScope() {
        CoursePlanningPeriods periods = new CoursePlanningPeriods();
        java.util.function.BiFunction<PaymentPlanCadence, Integer, CourseInvoicePlanningFacts> facts = (cadence, scope) -> new CourseInvoicePlanningFacts(1L, MARCH, MARCH.atDay(1),
                true, false, true, cadence, MARCH, scope, p -> true, null, Set.of(), 0, 10);

        assertThat(periods.periodsToPlan(facts.apply(PaymentPlanCadence.MONTHLY, null))).containsExactly(MARCH);
        assertThat(periods.periodsToPlan(facts.apply(PaymentPlanCadence.ANNUAL_UPFRONT, 3))).containsExactly(MARCH, YearMonth.of(2026, 4), YearMonth.of(2026, 5));
        assertThat(periods.periodsToPlan(facts.apply(PaymentPlanCadence.ANNUAL_UPFRONT, 0))).isEmpty();
        assertThat(periods.periodsToPlan(facts.apply(PaymentPlanCadence.ANNUAL_UPFRONT, null))).isEmpty();
        assertThat(periods.modeOf(facts.apply(PaymentPlanCadence.MONTHLY, null))).isEqualTo(CourseInstallmentMode.MONTHLY);
        assertThat(periods.modeOf(facts.apply(PaymentPlanCadence.ANNUAL_UPFRONT, 3))).isEqualTo(CourseInstallmentMode.UPFRONT);
        assertThat(CourseInstallmentMode.UPFRONT.upfront()).isTrue();
        assertThat(CourseInstallmentMode.MONTHLY.upfront()).isFalse();
        assertThat(CourseInstallmentMode.MONTHLY.decisionCode()).isEqualTo("ISSUE_INSTALLMENT");
        assertThat(CourseInstallmentMode.UPFRONT.decisionCode()).isEqualTo("ISSUE_UPFRONT_INSTALLMENT");
    }

    @Test
    @Tag("D-057")
    void anInstallmentDueDateIsTheIssueDatePlusTheTemplateDays() {
        assertThat(new MonthlyInstallmentDueDateCalculator().calculateInstallmentDueDate(LocalDate.of(2026, 1, 31), 10)).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(new MonthlyInstallmentDueDateCalculator().calculateInstallmentDueDate(LocalDate.of(2028, 2, 20), 10)).isEqualTo(LocalDate.of(2028, 3, 1));
    }

    @Test
    @Tag("D-166")
    @Tag("D-027")
    void theObligationStatusFollowsTheDocumentedPrecedence() {
        EnrollmentFeeObligationStatusClassifier classifier = new EnrollmentFeeObligationStatusClassifier();
        java.util.function.BiFunction<Boolean, ObligationInvoiceFacts, EnrollmentFeeObligationStatus> classify = (exempted, invoice) ->
                classifier.classifyEnrollmentFeeObligation(new EnrollmentFeeObligationStatusClassifier.Evidence(exempted, Optional.ofNullable(invoice)));

        assertThat(classify.apply(true, null)).isEqualTo(EnrollmentFeeObligationStatus.EXEMPTED);
        assertThat(classify.apply(false, null)).isEqualTo(EnrollmentFeeObligationStatus.NOT_ISSUED);
        assertThat(classify.apply(true, new ObligationInvoiceFacts(InvoiceStatus.PAID, false, true))).isEqualTo(EnrollmentFeeObligationStatus.PAID);
        assertThat(classify.apply(false, new ObligationInvoiceFacts(InvoiceStatus.WAIVED, false, true))).isEqualTo(EnrollmentFeeObligationStatus.WAIVED);
        assertThat(classify.apply(true, new ObligationInvoiceFacts(InvoiceStatus.CANCELLED, false, true))).isEqualTo(EnrollmentFeeObligationStatus.EXEMPTED);
        assertThat(classify.apply(false, new ObligationInvoiceFacts(InvoiceStatus.CANCELLED, true, true))).isEqualTo(EnrollmentFeeObligationStatus.CANCELLED);
        assertThat(classify.apply(false, new ObligationInvoiceFacts(InvoiceStatus.PENDING, true, true))).isEqualTo(EnrollmentFeeObligationStatus.IN_REVIEW);
        assertThat(classify.apply(false, new ObligationInvoiceFacts(InvoiceStatus.PENDING, false, true))).isEqualTo(EnrollmentFeeObligationStatus.PENDING);
        assertThat(classify.apply(false, new ObligationInvoiceFacts(InvoiceStatus.PENDING, false, false))).isEqualTo(EnrollmentFeeObligationStatus.PARTIAL);
        assertThat(EnrollmentFeeObligationStatus.PAID.isSatisfied()).isTrue();
        assertThat(EnrollmentFeeObligationStatus.WAIVED.isSatisfied()).isTrue();
        assertThat(EnrollmentFeeObligationStatus.EXEMPTED.isSatisfied()).isTrue();
        assertThat(EnrollmentFeeObligationStatus.PENDING.isSatisfied()).isFalse();
    }

    @Test
    @Tag("D-010")
    void theReferenceSlotIsTheLatestSlotDueInCatchUpAndTheSlotCoveringThePeriod() {
        EnrollmentFeeReferenceSlotResolver resolver = new EnrollmentFeeReferenceSlotResolver();
        ProgramYearBillingTemplate two = com.red_escuela.backend.billing.planning.policy.PlanningFixtures_.template();
        two.configureEnrollmentFees(2, 2, 8);
        YearCycles cycles = new YearCycles(List.of(YearCycle.of(two, 2026)), null);
        java.util.function.Function<YearMonth, ProgramInvoicePlanningFacts> at = month -> new ProgramInvoicePlanningFacts(1L, month, month.atDay(1), true, false, false, false, false,
                null, PaymentPlanCoverage.none(), true, cycles, List.of(), Set.of(), Map.of(), null, null, null);

        assertThat(resolver.resolveLatestDueSlot(at.apply(YearMonth.of(2026, 5))).orElseThrow().slot()).isEqualTo(1);
        assertThat(resolver.resolveLatestDueSlot(at.apply(YearMonth.of(2026, 9))).orElseThrow().slot()).isEqualTo(2);
        assertThat(resolver.resolveLatestDueSlot(at.apply(YearMonth.of(2030, 9)))).isEmpty();
        assertThat(resolver.resolveReferenceSlotForPeriod(at.apply(MARCH), YearMonth.of(2026, 9)).orElseThrow().slot()).isEqualTo(2);
        assertThat(resolver.resolveReferenceSlotForPeriod(at.apply(MARCH), YearMonth.of(2026, 3)).orElseThrow().slot()).isEqualTo(1);
        assertThat(resolver.resolveReferenceSlotForPeriod(at.apply(MARCH), YearMonth.of(2031, 3))).isEmpty();
    }

    @Test
    @Tag("D-166")
    void outcomesBlockOrIssueOnDemandOnlyWhenTheRequirementIsViolated() {
        java.util.List<PlannedInvoice> issued = new java.util.ArrayList<>();
        PlannedInvoice charge = new PlannedInvoice(null, MARCH, MARCH.atDay(1), MARCH.atDay(1), 1L, 1, "x", true, false);
        com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult denied = new com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult(
                List.of(PolicyVerdict.deny("EnrollmentFeeSettledPolicy", "ENROLLMENT_FEE_NOT_PAID_FOR_TERM", "x")));
        java.util.function.Function<List<com.red_escuela.backend.shared.domain.policy.PolicyViolation>, IllegalStateException> blocked = v -> new IllegalStateException("blocked");

        new EnrollmentFeeRequirementOutcome.Settled().resolveEnrollmentFeeRequirement(issued::add, blocked);
        new EnrollmentFeeRequirementOutcome.NotRequired("X").resolveEnrollmentFeeRequirement(issued::add, blocked);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new EnrollmentFeeRequirementOutcome.Block(denied).resolveEnrollmentFeeRequirement(issued::add, blocked))
                .isInstanceOf(IllegalStateException.class);
        assertThat(issued).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new EnrollmentFeeRequirementOutcome.IssueOnDemandAndBlock(charge, denied).resolveEnrollmentFeeRequirement(issued::add, blocked))
                .isInstanceOf(IllegalStateException.class);
        assertThat(issued).containsExactly(charge);
    }
}
