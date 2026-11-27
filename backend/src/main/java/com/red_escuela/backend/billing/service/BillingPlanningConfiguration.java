package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.planning.domain.InstallmentBusinessDays;
import com.red_escuela.backend.billing.planning.policy.CourseInstallmentPlanningContext;
import com.red_escuela.backend.billing.planning.policy.EligibilityPlanningContext;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeRequirementContext;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotPlanningContext;
import com.red_escuela.backend.billing.planning.policy.MonthlyInstallmentPlanningContext;
import com.red_escuela.backend.billing.planning.policy.PlanningPolicySetsConfiguration;
import com.red_escuela.backend.billing.planning.service.CourseMonthlyInvoicePlanner;
import com.red_escuela.backend.billing.planning.service.ProgramMonthlyInvoicePlanner;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingPlanningConfiguration {

    @Bean
    public PlanningPolicySetsConfiguration planningPolicySets() {
        return new PlanningPolicySetsConfiguration();
    }

    @Bean
    public ProgramMonthlyInvoicePlanner monthlyChargePlanner(PlanningPolicySetsConfiguration policySets, InstallmentBusinessDays billingCalendar) {
        return new ProgramMonthlyInvoicePlanner(policySets, billingCalendar);
    }

    @Bean
    public CourseMonthlyInvoicePlanner courseMonthlyInvoicePlanner(PlanningPolicySetsConfiguration policySets, InstallmentBusinessDays billingCalendar) {
        return new CourseMonthlyInvoicePlanner(policySets, billingCalendar);
    }

    @Bean
    public PolicySet<EligibilityPlanningContext> programInvoicePlanningEligibilityPolicySet(PlanningPolicySetsConfiguration policySets) {
        return policySets.programEligibility();
    }

    @Bean
    public PolicySet<EligibilityPlanningContext> courseInvoicePlanningEligibilityPolicySet(PlanningPolicySetsConfiguration policySets) {
        return policySets.courseEligibility();
    }

    @Bean
    public PolicySet<EnrollmentFeeSlotPlanningContext> enrollmentFeePlanningPolicySet(PlanningPolicySetsConfiguration policySets) {
        return policySets.enrollmentFeePlanning();
    }

    @Bean
    public PolicySet<MonthlyInstallmentPlanningContext> monthlyInstallmentPlanningPolicySet(PlanningPolicySetsConfiguration policySets) {
        return policySets.monthlyInstallmentPlanning();
    }

    @Bean
    public PolicySet<CourseInstallmentPlanningContext> courseMonthlyInstallmentPlanningPolicySet(PlanningPolicySetsConfiguration policySets) {
        return policySets.courseMonthlyInstallmentPlanning();
    }

    @Bean
    public PolicySet<EnrollmentFeeRequirementContext> enrollmentFeeRequirementApplicabilityPolicySet(PlanningPolicySetsConfiguration policySets) {
        return policySets.enrollmentFeeRequirementApplicability();
    }

    @Bean
    public PolicySet<EnrollmentFeeRequirementContext> enrollmentFeeRequirementPolicySet(PlanningPolicySetsConfiguration policySets) {
        return policySets.enrollmentFeeRequirement();
    }
}
