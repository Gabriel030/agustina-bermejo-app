package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;

import java.util.List;
import java.util.Set;

public final class PlanningPolicySetsConfiguration {
    private final PolicySet<EligibilityPlanningContext> programEligibility = PolicySet.named("PROGRAM_INVOICE_PLANNING_ELIGIBILITY", List.of(
            new AcademicallyActiveEnrollmentPolicy(),
            new NotSuspendedOrWithdrawnForNonPaymentPolicy(),
            new StudyPlanNotCompletedPolicy(),
            new PaymentPlanChosenPolicy()));

    private final PolicySet<EligibilityPlanningContext> courseEligibility = PolicySet.named("COURSE_INVOICE_PLANNING_ELIGIBILITY", List.of(
            new AcademicallyActiveEnrollmentPolicy(),
            new NotSuspendedOrWithdrawnForNonPaymentPolicy(),
            new PaymentPlanChosenPolicy()));

    private final PolicySet<EnrollmentFeeSlotPlanningContext> enrollmentFeePlanning = PolicySet.named("ENROLLMENT_FEE_PLANNING", List.of(
            new BillingWhilePausedPolicy<EnrollmentFeeSlotPlanningContext>(),
            new FinalExamsOnlyExemptionPolicy<EnrollmentFeeSlotPlanningContext>(FeeType.ENROLLMENT_FEE, Set.of(BillingConsultation.PLANNING), "ONLY_FINAL_EXAMS"),
            new EnrollmentFeeSlotNotAlreadyIssuedPolicy()));

    private final PolicySet<MonthlyInstallmentPlanningContext> monthlyInstallmentPlanning = PolicySet.named("MONTHLY_INSTALLMENT_PLANNING", List.of(
            new NotBeforeEntryMonthPolicy(),
            new MonthlyCadencePolicy(),
            new InstallmentWithinLectiveYearRangePolicy(),
            new BillingWhilePausedPolicy<MonthlyInstallmentPlanningContext>(),
            new InstallmentCapPolicy<MonthlyInstallmentPlanningContext>(),
            new FinalExamsOnlyExemptionPolicy<MonthlyInstallmentPlanningContext>(FeeType.MONTHLY_INSTALLMENT, Set.of(BillingConsultation.PLANNING), "ONLY_FINAL_EXAMS"),
            new MonthlyInstallmentNotAlreadyIssuedPolicy<MonthlyInstallmentPlanningContext>()));

    private final PolicySet<CourseInstallmentPlanningContext> courseMonthlyInstallmentPlanning = PolicySet.named("COURSE_MONTHLY_INSTALLMENT_PLANNING", List.of(
            new MonthlyInstallmentNotAlreadyIssuedPolicy<CourseInstallmentPlanningContext>(),
            new CourseInstallmentWithinTermPolicy(),
            new InstallmentCapPolicy<CourseInstallmentPlanningContext>()));

    private final PolicySet<EnrollmentFeeRequirementContext> enrollmentFeeRequirementApplicability = PolicySet.named("ENROLLMENT_FEE_REQUIREMENT_APPLICABILITY", List.<Policy<EnrollmentFeeRequirementContext>>of(
            new CertificateEnrollmentFeeExemptionPolicy(),
            new EnrollmentFeeBlocksScopePolicy(),
            new FinalExamsOnlyExemptionPolicy<EnrollmentFeeRequirementContext>(FeeType.ENROLLMENT_FEE,
                    Set.of(BillingConsultation.EXAM_REGISTRATION, BillingConsultation.CERTIFICATE), "NOT_REQUIRED_ONLY_FINAL_EXAMS"),
            new EnrollmentFeeSlotNotBeforeEntryPolicy(),
            new EnrollmentFeeExemptionGrantedPolicy()));

    private final PolicySet<EnrollmentFeeRequirementContext> enrollmentFeeRequirement = PolicySet.named("ENROLLMENT_FEE_REQUIREMENT", List.of(
            new EnrollmentFeeSettledPolicy()));

    public PolicySet<EligibilityPlanningContext> programEligibility() {
        return programEligibility;
    }

    public PolicySet<EligibilityPlanningContext> courseEligibility() {
        return courseEligibility;
    }

    public PolicySet<EnrollmentFeeSlotPlanningContext> enrollmentFeePlanning() {
        return enrollmentFeePlanning;
    }

    public PolicySet<MonthlyInstallmentPlanningContext> monthlyInstallmentPlanning() {
        return monthlyInstallmentPlanning;
    }

    public PolicySet<CourseInstallmentPlanningContext> courseMonthlyInstallmentPlanning() {
        return courseMonthlyInstallmentPlanning;
    }

    public PolicySet<EnrollmentFeeRequirementContext> enrollmentFeeRequirementApplicability() {
        return enrollmentFeeRequirementApplicability;
    }

    public PolicySet<EnrollmentFeeRequirementContext> enrollmentFeeRequirement() {
        return enrollmentFeeRequirement;
    }
}
