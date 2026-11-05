package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentDiscountPolicyRuleNamesTest {

    @Test
    void everyDiscountPolicyReportsItsClassNameAsRuleName() {
        List<Policy<?>> policies = List.of(
                new DiscountDefinitionCoherencePolicy(), new DiscountAvailabilityPeriodPolicy(), new DiscountScopeMatchesTypePolicy(), new CohortPromotionTermsPolicy(),
                new ScholarshipOnlyForMonthlyInstallmentsPolicy(), new DiscountTypeAvailableInCatalogPolicy(), new InstallmentCountOnlyForMonthlyInstallmentsPolicy(),
                new DiscountCurrencyMatchesOfferingPolicy(), new DiscountClosureReasonRequiredPolicy(), new StudentDiscountCoherencePolicy(), new ProposalNotExpiredPolicy(),
                new StudentDiscountDefinitionPolicy(), new StudentDiscountInvoiceTypePolicy(), new StudentDiscountPeriodRangePolicy(), new StudentDiscountCurrencyPolicy(),
                new ScholarshipStartsAtNextInstallmentPolicy(), new ScholarshipNotAvailableWithAnnualUpfrontPolicy(), new StudentDiscountNoOverlapPolicy(),
                new EnrollmentFeeDiscountBeforeIssuePolicy(), new RevocationReasonRequiredPolicy(), new RevocationProposalNotExpiredPolicy());

        assertThat(policies).allSatisfy(policy -> assertThat(policy.ruleName()).isEqualTo(policy.getClass().getSimpleName()));
    }
}
