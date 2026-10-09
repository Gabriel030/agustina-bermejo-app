package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.planning.domain.CourseInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.billing.planning.domain.IssuedEnrollmentFee;
import com.red_escuela.backend.billing.planning.domain.LectiveYearCycleResolution;
import com.red_escuela.backend.billing.planning.domain.PaymentPlanCoverage;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.shared.domain.policy.Policy;
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

class PlanningPoliciesTest {
    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    private static void assertDenies(Policy<?> policy, PolicyVerdict verdict, String code) {
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo(code);
        assertThat(verdict.policy()).isEqualTo(policy.ruleName());
    }

    private static EligibilityPlanningContext eligibility(boolean active, boolean suspended, boolean completed, boolean chosen) {
        return new EligibilityPlanningContext(PlanningSubject.PROGRAM, active, suspended, completed, chosen, PaymentPlanCadence.MONTHLY);
    }

    @Test
    @Tag("D-050")
    void eligibilityPoliciesDenyWithTheirOwnCodeAndQuestionOne() {
        AcademicallyActiveEnrollmentPolicy active = new AcademicallyActiveEnrollmentPolicy();
        NotSuspendedOrWithdrawnForNonPaymentPolicy suspended = new NotSuspendedOrWithdrawnForNonPaymentPolicy();
        StudyPlanNotCompletedPolicy completed = new StudyPlanNotCompletedPolicy();
        PaymentPlanChosenPolicy chosen = new PaymentPlanChosenPolicy();

        assertThat(active.evaluateRule(eligibility(true, false, false, true)).allowed()).isTrue();
        assertDenies(active, active.evaluateRule(eligibility(false, false, false, true)), "NOT_ACTIVE");
        assertThat(active.evaluateRule(eligibility(false, false, false, true)).reason()).isEqualTo(PlanningSubject.PROGRAM.inactiveDetail());
        assertThat(suspended.evaluateRule(eligibility(true, false, false, true)).allowed()).isTrue();
        assertDenies(suspended, suspended.evaluateRule(eligibility(true, true, false, true)), "SUSPENDED_OR_WITHDRAWN");
        assertThat(completed.evaluateRule(eligibility(true, false, false, true)).allowed()).isTrue();
        assertDenies(completed, completed.evaluateRule(eligibility(true, false, true, true)), "PLAN_COMPLETED");
        assertThat(chosen.evaluateRule(eligibility(true, false, false, true)).allowed()).isTrue();
        assertDenies(chosen, chosen.evaluateRule(eligibility(true, false, false, false)), "NO_PAYMENT_PLAN");
        assertThat(List.of(active.plannerQuestionNumber(), suspended.plannerQuestionNumber(), completed.plannerQuestionNumber(), chosen.plannerQuestionNumber()))
                .containsOnly(1);
    }

    @Test
    @Tag("D-040")
    void courseEligibilityRequiresAPlanWithACadence() {
        CourseInvoicePlanningFacts noCadence = new CourseInvoicePlanningFacts(1L, MARCH, MARCH.atDay(1), true, false, true, null, null, null, p -> true, null,
                Set.of(), 0, 10);
        CourseInvoicePlanningFacts withCadence = new CourseInvoicePlanningFacts(1L, MARCH, MARCH.atDay(1), true, false, true, PaymentPlanCadence.MONTHLY, null,
                null, p -> true, null, Set.of(), 0, 10);

        assertThat(EligibilityPlanningContext.ofCourse(noCadence).paymentPlanChosen()).isFalse();
        assertThat(EligibilityPlanningContext.ofCourse(withCadence).paymentPlanChosen()).isTrue();
        assertThat(EligibilityPlanningContext.ofCourse(withCadence).subject()).isEqualTo(PlanningSubject.COURSE);
    }

    @Test
    @Tag("D-050")
    void billingWhilePausedDeniesOnlyWhenPausedAndTheTemplateDoesNotChargeWhilePaused() {
        BillingWhilePausedPolicy<EnrollmentFeeSlotPlanningContext> policy = new BillingWhilePausedPolicy<>();
        ProgramYearBillingTemplate template = PlanningFixtures.template(1, 2026, 10, 1, 2, null);
        YearCycle cycle = com.red_escuela.backend.billing.planning.TestTemplateTerms.cycleOf(template, 2026);

        EnrollmentFeeSlotPlanningContext paused = EnrollmentFeeSlotPlanningContext.of(PlanningFixtures.facts(template, MARCH, true, false), cycle, 1, false);
        EnrollmentFeeSlotPlanningContext active = EnrollmentFeeSlotPlanningContext.of(PlanningFixtures.facts(template, MARCH, false, false), cycle, 1, false);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setPaysWhilePaused(template, true);
        cycle = com.red_escuela.backend.billing.planning.TestTemplateTerms.cycleOf(template, 2026);
        EnrollmentFeeSlotPlanningContext pausedButPays = EnrollmentFeeSlotPlanningContext.of(PlanningFixtures.facts(template, MARCH, true, false), cycle, 1, false);

        assertDenies(policy, policy.evaluateRule(paused), "PAUSED_NOT_BILLED");
        assertThat(policy.evaluateRule(paused).reason()).isEqualTo("plantilla 1 matrícula 1 no se emite: inscripción en pausa académica");
        assertThat(policy.evaluateRule(active).allowed()).isTrue();
        assertThat(policy.evaluateRule(pausedButPays).allowed()).isTrue();
        assertThat(policy.plannerQuestionNumber()).isEqualTo(1);
    }

    @Test
    @Tag("D-016")
    @Tag("D-037")
    void finalExamsOnlyExemptionDependsOnTheFeeTypeAndTheConsultation() {
        ProgramYearBillingTemplate template = PlanningFixtures.template(1, 2026, 10, 1, 2, null);
        YearCycle cycle = com.red_escuela.backend.billing.planning.TestTemplateTerms.cycleOf(template, 2026);
        ProgramInvoicePlanningFacts onlyFinals = PlanningFixtures.facts(template, MARCH, false, true);
        ProgramInvoicePlanningFacts regular = PlanningFixtures.facts(template, MARCH, false, false);
        FinalExamsOnlyExemptionPolicy<ProgramFactsContext> fee = new FinalExamsOnlyExemptionPolicy<>(FeeType.ENROLLMENT_FEE, Set.of(BillingConsultation.PLANNING), "ONLY_FINAL_EXAMS");
        FinalExamsOnlyExemptionPolicy<ProgramFactsContext> installment = new FinalExamsOnlyExemptionPolicy<>(FeeType.MONTHLY_INSTALLMENT, Set.of(BillingConsultation.PLANNING), "ONLY_FINAL_EXAMS");
        FinalExamsOnlyExemptionPolicy<ProgramFactsContext> examRegistration = new FinalExamsOnlyExemptionPolicy<>(FeeType.ENROLLMENT_FEE,
                Set.of(BillingConsultation.EXAM_REGISTRATION, BillingConsultation.CERTIFICATE), "NOT_REQUIRED_ONLY_FINAL_EXAMS");

        EnrollmentFeeSlotPlanningContext planning = EnrollmentFeeSlotPlanningContext.of(onlyFinals, cycle, 1, false);
        assertDenies(fee, fee.evaluateRule(planning), "ONLY_FINAL_EXAMS");
        assertThat(fee.evaluateRule(planning).reason()).isEqualTo("plantilla 1 matrícula 1 no se emite: solo debe finales");
        assertThat(fee.evaluateRule(EnrollmentFeeSlotPlanningContext.of(regular, cycle, 1, false)).allowed()).isTrue();
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setRequireEnrollmentFeeForFinalExamOnlyStudents(template, true);
        cycle = com.red_escuela.backend.billing.planning.TestTemplateTerms.cycleOf(template, 2026);
        assertThat(fee.evaluateRule(EnrollmentFeeSlotPlanningContext.of(onlyFinals, cycle, 1, false)).allowed()).isTrue();
        MonthlyInstallmentPlanningContext installmentContext = new MonthlyInstallmentPlanningContext(onlyFinals,
                new LectiveYearCycleResolution.Configured(cycle, Optional.of(cycle), true));
        assertDenies(installment, installment.evaluateRule(installmentContext), "ONLY_FINAL_EXAMS");
        assertThat(installment.evaluateRule(installmentContext).reason()).isEqualTo("no se emite cuota: solo debe finales");
        assertThat(fee.plannerQuestionNumber()).isEqualTo(6);

        com.red_escuela.backend.billing.planning.TestTemplateTerms.setRequireEnrollmentFeeForFinalExamOnlyStudents(template, false);

        cycle = com.red_escuela.backend.billing.planning.TestTemplateTerms.cycleOf(template, 2026);
        EnrollmentFeeRequirementContext exam = new EnrollmentFeeRequirementContext(EnrollmentFeeRequirementScope.EXAM_REGISTRATION, onlyFinals, cycle, 1, cycle.terms(), false, Optional.empty());
        EnrollmentFeeRequirementContext subject = new EnrollmentFeeRequirementContext(EnrollmentFeeRequirementScope.SUBJECT_ENROLLMENT, onlyFinals, cycle, 1, cycle.terms(), false, Optional.empty());
        assertDenies(examRegistration, examRegistration.evaluateRule(exam), "NOT_REQUIRED_ONLY_FINAL_EXAMS");
        assertThat(examRegistration.evaluateRule(exam).reason()).isEqualTo("no se exige: solo debe finales");
        assertThat(examRegistration.evaluateRule(subject).allowed()).isTrue();
    }

    @Test
    @Tag("D-009")
    void aSlotAlreadyIssuedDeniesWithAlreadyIssuedOrFeeCancelled() {
        EnrollmentFeeSlotNotAlreadyIssuedPolicy policy = new EnrollmentFeeSlotNotAlreadyIssuedPolicy();
        ProgramYearBillingTemplate template = PlanningFixtures.template(1, 2026, 10, 1, 2, null);
        YearCycle cycle = com.red_escuela.backend.billing.planning.TestTemplateTerms.cycleOf(template, 2026);
        java.util.function.Function<InvoicePaymentStatus, EnrollmentFeeSlotPlanningContext> withFee = status -> EnrollmentFeeSlotPlanningContext.of(
                PlanningFixtures.facts(template, MARCH, MARCH.atDay(1), false, false, null, PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY),
                        List.of(new IssuedEnrollmentFee(5L, 1L, 1, YearMonth.of(2026, 2), status, LocalDate.of(2026, 2, 1))), Set.of(), Map.of()), cycle, 1, false);

        assertThat(policy.evaluateRule(EnrollmentFeeSlotPlanningContext.of(PlanningFixtures.facts(template, MARCH, false, false), cycle, 1, false)).allowed()).isTrue();
        assertDenies(policy, policy.evaluateRule(withFee.apply(InvoicePaymentStatus.PENDING)), "ALREADY_ISSUED");
        assertDenies(policy, policy.evaluateRule(withFee.apply(InvoicePaymentStatus.PAID)), "ALREADY_ISSUED");
        assertDenies(policy, policy.evaluateRule(withFee.apply(InvoicePaymentStatus.CANCELLED)), "FEE_CANCELLED");
        assertThat(policy.evaluateRule(withFee.apply(InvoicePaymentStatus.PENDING)).reason()).isEqualTo("plantilla 1 matrícula 1");
        assertThat(policy.plannerQuestionNumber()).isEqualTo(7);
    }

    private static MonthlyInstallmentPlanningContext installment(ProgramYearBillingTemplate template, YearMonth month, YearMonth entry, PaymentPlanCoverage coverage,
                                                                Set<YearMonth> recurring) {
        ProgramInvoicePlanningFacts facts = PlanningFixtures.facts(template, month, month.atDay(1), false, false, entry, coverage, List.of(), recurring, Map.of());
        Optional<YearCycle> cycle = facts.cycles().forInstallment(month);
        LectiveYearCycleResolution resolution = cycle.<LectiveYearCycleResolution>map(c -> new LectiveYearCycleResolution.Configured(c, cycle, true))
                .orElseGet(() -> new LectiveYearCycleResolution.Configured(facts.cycles().all().get(0), Optional.empty(), facts.cycles().covering(month).isPresent()));
        return new MonthlyInstallmentPlanningContext(facts, resolution);
    }

    @Test
    @Tag("D-049")
    void notBeforeEntryMonthDeniesOnlyForMonthsStrictlyBeforeTheEntry() {
        NotBeforeEntryMonthPolicy policy = new NotBeforeEntryMonthPolicy();
        ProgramYearBillingTemplate template = PlanningFixtures.template(1, 2026, 10, 1, 2, null);
        PaymentPlanCoverage monthly = PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY);

        assertDenies(policy, policy.evaluateRule(installment(template, MARCH, YearMonth.of(2026, 4), monthly, Set.of())), "BEFORE_ENTRY");
        assertThat(policy.evaluateRule(installment(template, MARCH, YearMonth.of(2026, 4), monthly, Set.of())).reason()).isEqualTo("la cursada del alumno empieza en 2026-04");
        assertThat(policy.evaluateRule(installment(template, MARCH, MARCH, monthly, Set.of())).allowed()).isTrue();
        assertThat(policy.evaluateRule(installment(template, MARCH, null, monthly, Set.of())).allowed()).isTrue();
        assertThat(policy.plannerQuestionNumber()).isEqualTo(2);
    }

    @Test
    @Tag("D-040")
    void monthlyCadenceDeniesUpfrontPlansUntilTheirAnnualScopeExpires() {
        MonthlyCadencePolicy policy = new MonthlyCadencePolicy();
        ProgramYearBillingTemplate template = PlanningFixtures.template(1, 2026, 10, 1, 2, null);
        PaymentPlanCoverage upfront = new PaymentPlanCoverage(PaymentPlanCadence.ANNUAL_UPFRONT, MARCH, 10);

        assertThat(policy.evaluateRule(installment(template, MARCH, null, PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY), Set.of())).allowed()).isTrue();
        assertDenies(policy, policy.evaluateRule(installment(template, YearMonth.of(2026, 12), null, upfront, Set.of())), "PLAN_NOT_MONTHLY");
        assertThat(policy.evaluateRule(installment(template, YearMonth.of(2026, 12), null, upfront, Set.of())).reason()).isEqualTo("el plan vigente es ANNUAL_UPFRONT");
        assertThat(upfront.isAnnualScopeExpiredAt(YearMonth.of(2026, 12))).isFalse();
        assertThat(upfront.isAnnualScopeExpiredAt(YearMonth.of(2027, 1))).isTrue();
        assertThat(upfront.effectiveCadenceForMonth(YearMonth.of(2027, 1))).isEqualTo(PaymentPlanCadence.MONTHLY);
        assertThat(upfront.effectiveCadenceForMonth(YearMonth.of(2026, 12))).isEqualTo(PaymentPlanCadence.ANNUAL_UPFRONT);
        assertThat(PaymentPlanCoverage.none().effectiveCadenceForMonth(MARCH)).isNull();
        assertThat(PaymentPlanCoverage.ofCadence(PaymentPlanCadence.ANNUAL_UPFRONT).isAnnualScopeExpiredAt(YearMonth.of(2040, 1))).isFalse();
        assertThat(new PaymentPlanCoverage(PaymentPlanCadence.MONTHLY, MARCH, 1).isAnnualScopeExpiredAt(YearMonth.of(2040, 1))).isFalse();
    }

    @Test
    @Tag("D-001")
    @Tag("D-006")
    void installmentWithinLectiveYearRangeDeniesOutsideTheTermAndWhenTheYearIsNotConfigured() {
        InstallmentWithinLectiveYearRangePolicy policy = new InstallmentWithinLectiveYearRangePolicy();
        ProgramYearBillingTemplate template = PlanningFixtures.template(1, 2026, 10, 1, 2, null);
        PaymentPlanCoverage monthly = PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY);

        assertThat(policy.evaluateRule(installment(template, MARCH, null, monthly, Set.of())).allowed()).isTrue();
        assertDenies(policy, policy.evaluateRule(installment(template, YearMonth.of(2026, 2), null, monthly, Set.of())), "OUTSIDE_TERM");
        assertThat(policy.evaluateRule(installment(template, YearMonth.of(2026, 2), null, monthly, Set.of())).reason()).isEqualTo("sin cuota en 2026-02");
        MonthlyInstallmentPlanningContext notConfigured = new MonthlyInstallmentPlanningContext(PlanningFixtures.facts(template, YearMonth.of(2030, 5), false, false),
                new LectiveYearCycleResolution.NotConfigured(YearMonth.of(2030, 5)));
        assertDenies(policy, policy.evaluateRule(notConfigured), "YEAR_NOT_CONFIGURED");
    }

    @Test
    @Tag("D-034")
    void installmentCapDeniesWhenTheIssuedInstallmentsReachTheCap() {
        InstallmentCapPolicy<MonthlyInstallmentPlanningContext> policy = new InstallmentCapPolicy<>();
        ProgramYearBillingTemplate template = PlanningFixtures.template(1, 2026, 3, 1, 2, null);
        PaymentPlanCoverage monthly = PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY);
        Set<YearMonth> two = Set.of(YearMonth.of(2026, 3), YearMonth.of(2026, 4));
        Set<YearMonth> three = Set.of(YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5));

        assertThat(policy.evaluateRule(installment(template, YearMonth.of(2026, 6), null, monthly, two)).allowed()).isTrue();
        assertDenies(policy, policy.evaluateRule(installment(template, YearMonth.of(2026, 6), null, monthly, three)), "INSTALLMENT_CAP_REACHED");
        assertThat(policy.evaluateRule(installment(template, YearMonth.of(2026, 6), null, monthly, three)).reason()).isEqualTo("3 de 3");
        assertThat(policy.plannerQuestionNumber()).isEqualTo(5);
    }

    @Test
    @Tag("D-039")
    void courseCapAndTermAndAlreadyIssuedPoliciesUseTheirBoundaries() {
        InstallmentCapPolicy<CourseInstallmentPlanningContext> cap = new InstallmentCapPolicy<>();
        CourseInstallmentWithinTermPolicy term = new CourseInstallmentWithinTermPolicy();
        MonthlyInstallmentNotAlreadyIssuedPolicy<CourseInstallmentPlanningContext> issued = new MonthlyInstallmentNotAlreadyIssuedPolicy<>();
        CourseInvoicePlanningFacts capped = new CourseInvoicePlanningFacts(1L, MARCH, MARCH.atDay(1), true, false, true, PaymentPlanCadence.MONTHLY, null, null,
                p -> !p.isBefore(MARCH) && !p.isAfter(YearMonth.of(2026, 6)), 2, Set.of(YearMonth.of(2026, 4)), 0, 10);
        CourseInvoicePlanningFacts unlimited = new CourseInvoicePlanningFacts(1L, MARCH, MARCH.atDay(1), true, false, true, PaymentPlanCadence.MONTHLY, null, null,
                p -> true, null, Set.of(), 0, 10);

        assertThat(cap.evaluateRule(new CourseInstallmentPlanningContext(capped, MARCH, 1)).allowed()).isTrue();
        assertDenies(cap, cap.evaluateRule(new CourseInstallmentPlanningContext(capped, MARCH, 2)), "INSTALLMENT_CAP_REACHED");
        assertThat(cap.evaluateRule(new CourseInstallmentPlanningContext(capped, MARCH, 2)).reason()).isEqualTo("2 de 2");
        assertThat(cap.evaluateRule(new CourseInstallmentPlanningContext(unlimited, MARCH, 99)).allowed()).isTrue();
        assertThat(term.evaluateRule(new CourseInstallmentPlanningContext(capped, YearMonth.of(2026, 6), 0)).allowed()).isTrue();
        assertDenies(term, term.evaluateRule(new CourseInstallmentPlanningContext(capped, YearMonth.of(2026, 7), 0)), "OUTSIDE_TERM");
        assertThat(term.evaluateRule(new CourseInstallmentPlanningContext(capped, YearMonth.of(2026, 7), 0)).reason()).isEqualTo("sin cuota en 2026-07");
        assertDenies(issued, issued.evaluateRule(new CourseInstallmentPlanningContext(capped, YearMonth.of(2026, 4), 0)), "ALREADY_ISSUED");
        assertThat(issued.evaluateRule(new CourseInstallmentPlanningContext(capped, YearMonth.of(2026, 5), 0)).allowed()).isTrue();
    }

    private static EnrollmentFeeRequirementContext requirement(EnrollmentFeeRequirementScope scope, ProgramYearBillingTemplate template, boolean active, boolean completed,
                                                              YearMonth entry, boolean exempted, Optional<IssuedEnrollmentFee> fee) {
        YearCycle cycle = com.red_escuela.backend.billing.planning.TestTemplateTerms.cycleOf(template, 2026);
        ProgramInvoicePlanningFacts base = PlanningFixtures.facts(template, MARCH, MARCH.atDay(1), false, false, entry,
                PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY), List.of(), Set.of(), Map.of());
        ProgramInvoicePlanningFacts facts = new ProgramInvoicePlanningFacts(1L, MARCH, MARCH.atDay(1), active, false, completed, false, false, entry,
                PaymentPlanCoverage.ofCadence(PaymentPlanCadence.MONTHLY), true, base.cycles(), List.of(), Set.of(), Map.of(), null, null, null);
        return new EnrollmentFeeRequirementContext(scope, facts, cycle, 1, cycle.terms(), exempted, fee);
    }

    @Test
    @Tag("D-015")
    @Tag("D-013")
    @Tag("D-020")
    @Tag("D-027")
    void requirementApplicabilityPoliciesExplainWhyTheFeeIsNotRequired() {
        ProgramYearBillingTemplate template = PlanningFixtures.template(1, 2026, 10, 1, 2, null);
        CertificateEnrollmentFeeExemptionPolicy certificate = new CertificateEnrollmentFeeExemptionPolicy();
        EnrollmentFeeBlocksScopePolicy blocks = new EnrollmentFeeBlocksScopePolicy();
        EnrollmentFeeSlotNotBeforeEntryPolicy beforeEntry = new EnrollmentFeeSlotNotBeforeEntryPolicy();
        EnrollmentFeeExemptionGrantedPolicy exemption = new EnrollmentFeeExemptionGrantedPolicy();
        EnrollmentFeeRequirementScope cert = EnrollmentFeeRequirementScope.CERTIFICATE;

        assertThat(certificate.evaluateRule(requirement(cert, template, true, false, null, false, Optional.empty())).allowed()).isTrue();
        assertDenies(certificate, certificate.evaluateRule(requirement(cert, template, false, false, null, false, Optional.empty())), "NOT_REQUIRED_FOR_INACTIVE_OR_COMPLETED");
        assertDenies(certificate, certificate.evaluateRule(requirement(cert, template, true, true, null, false, Optional.empty())), "NOT_REQUIRED_FOR_INACTIVE_OR_COMPLETED");
        assertThat(certificate.evaluateRule(requirement(EnrollmentFeeRequirementScope.EXAM_REGISTRATION, template, false, true, null, false, Optional.empty())).allowed()).isTrue();
        assertThat(blocks.evaluateRule(requirement(cert, template, true, false, null, false, Optional.empty())).allowed()).isTrue();
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksCertificates(template, false);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksExamRegistration(template, false);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksEnrollment(template, false);
        for (EnrollmentFeeRequirementScope scope : EnrollmentFeeRequirementScope.values()) {
            assertDenies(blocks, blocks.evaluateRule(requirement(scope, template, true, false, null, false, Optional.empty())), "NOT_REQUIRED_SCOPE_NOT_BLOCKED");
        }
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setBlocksEnrollment(template, true);
        assertThat(blocks.evaluateRule(requirement(EnrollmentFeeRequirementScope.SUBJECT_ENROLLMENT, template, true, false, null, false, Optional.empty())).allowed()).isTrue();
        assertThat(beforeEntry.evaluateRule(requirement(cert, template, true, false, MARCH, false, Optional.empty())).allowed()).isTrue();
        assertDenies(beforeEntry, beforeEntry.evaluateRule(requirement(cert, template, true, false, YearMonth.of(2027, 1), false, Optional.empty())), "NOT_REQUIRED_BEFORE_ENTRY");
        assertThat(exemption.evaluateRule(requirement(cert, template, true, false, null, false, Optional.empty())).allowed()).isTrue();
        assertDenies(exemption, exemption.evaluateRule(requirement(cert, template, true, false, null, true, Optional.empty())), "NOT_REQUIRED_EXEMPTION_GRANTED");
    }

    @Test
    @Tag("D-166")
    @Tag("D-010")
    void aFeeIsSettledOnlyWhenIssuedAndPaid() {
        EnrollmentFeeSettledPolicy policy = new EnrollmentFeeSettledPolicy();
        ProgramYearBillingTemplate template = PlanningFixtures.template(1, 2026, 10, 1, 2, null);
        EnrollmentFeeRequirementScope scope = EnrollmentFeeRequirementScope.SUBJECT_ENROLLMENT;
        java.util.function.Function<InvoicePaymentStatus, Optional<IssuedEnrollmentFee>> fee = status ->
                Optional.of(new IssuedEnrollmentFee(1L, 1L, 1, YearMonth.of(2026, 2), status, LocalDate.of(2026, 2, 1)));

        assertDenies(policy, policy.evaluateRule(requirement(scope, template, true, false, null, false, Optional.empty())), "ENROLLMENT_FEE_NOT_ISSUED");
        assertDenies(policy, policy.evaluateRule(requirement(scope, template, true, false, null, false, fee.apply(InvoicePaymentStatus.PENDING))), "ENROLLMENT_FEE_NOT_PAID_FOR_TERM");
        assertThat(policy.evaluateRule(requirement(scope, template, true, false, null, false, fee.apply(InvoicePaymentStatus.PAID))).allowed()).isTrue();
    }
}
