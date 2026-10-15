package com.red_escuela.backend.billing.discount;

import com.red_escuela.backend.billing.discount.policy.DiscountAvailabilityPeriodPolicy;
import com.red_escuela.backend.billing.discount.policy.DiscountClosureCheck;
import com.red_escuela.backend.billing.discount.policy.DiscountClosureReasonRequiredPolicy;
import com.red_escuela.backend.billing.discount.policy.DiscountCurrencyMatchesOfferingPolicy;
import com.red_escuela.backend.billing.discount.policy.DiscountDefinitionCheck;
import com.red_escuela.backend.billing.discount.policy.DiscountDefinitionCoherencePolicy;
import com.red_escuela.backend.billing.discount.policy.DiscountScopeMatchesTypePolicy;
import com.red_escuela.backend.billing.discount.policy.DiscountTypeAvailableInCatalogPolicy;
import com.red_escuela.backend.billing.discount.policy.InstallmentCountOnlyForMonthlyInstallmentsPolicy;
import com.red_escuela.backend.billing.discount.policy.RevocationReasonRequiredPolicy;
import com.red_escuela.backend.billing.discount.policy.ScholarshipNotAvailableWithAnnualUpfrontPolicy;
import com.red_escuela.backend.billing.discount.policy.ScholarshipOnlyForMonthlyInstallmentsPolicy;
import com.red_escuela.backend.billing.discount.policy.ScholarshipStartsAtNextInstallmentPolicy;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountCoherencePolicy;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountCurrencyPolicy;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountDefinitionPolicy;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountGrantCheck;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountInvoiceTypePolicy;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountPeriodRangePolicy;
import com.red_escuela.backend.billing.discount.policy.StudentDiscountRevocationCheck;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DiscountPolicySetsConfiguration {

    @Bean
    public PolicySet<DiscountDefinitionCheck> discountDefinitionPolicySet() {
        List<Policy<DiscountDefinitionCheck>> ordered = List.of(
                new DiscountDefinitionCoherencePolicy(),
                new DiscountAvailabilityPeriodPolicy(),
                new DiscountScopeMatchesTypePolicy(),
                new ScholarshipOnlyForMonthlyInstallmentsPolicy(),
                new DiscountTypeAvailableInCatalogPolicy(),
                new InstallmentCountOnlyForMonthlyInstallmentsPolicy(),
                new DiscountCurrencyMatchesOfferingPolicy());
        return PolicySet.named("DISCOUNT_DEFINITION", ordered);
    }

    @Bean
    public PolicySet<DiscountClosureCheck> discountDefinitionClosurePolicySet() {
        return PolicySet.named("DISCOUNT_DEFINITION_CLOSURE", List.of(new DiscountClosureReasonRequiredPolicy()));
    }

    @Bean
    public PolicySet<StudentDiscountGrantCheck> studentDiscountGrantingPolicySet() {
        List<Policy<StudentDiscountGrantCheck>> ordered = List.of(
                new StudentDiscountCoherencePolicy(),
                new StudentDiscountDefinitionPolicy(),
                new StudentDiscountInvoiceTypePolicy(),
                new StudentDiscountPeriodRangePolicy(),
                new StudentDiscountCurrencyPolicy(),
                new ScholarshipStartsAtNextInstallmentPolicy(),
                new ScholarshipNotAvailableWithAnnualUpfrontPolicy());
        return PolicySet.named("STUDENT_DISCOUNT_GRANTING", ordered);
    }

    @Bean
    public PolicySet<StudentDiscountRevocationCheck> studentDiscountRevocationPolicySet() {
        return PolicySet.named("STUDENT_DISCOUNT_REVOCATION", List.of(new RevocationReasonRequiredPolicy()));
    }
}
