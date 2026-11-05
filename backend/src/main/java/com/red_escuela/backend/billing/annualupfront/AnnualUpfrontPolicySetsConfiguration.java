package com.red_escuela.backend.billing.annualupfront;

import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontBlockedByOverdueDebtPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontEnrollmentActivePolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontLineCountSupportedPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontMonthsStillAvailablePolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontPriceAvailablePolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontRequestCheck;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontRequestWindowPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontIdempotencyKey;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontIdempotencyKeyPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontTermsAcceptance;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontTermsAcceptedPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.OnlyOneAnnualUpfrontRequestPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.ScholarshipHolderCannotChooseAnnualUpfrontPolicy;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontPlanCannotBeChangedPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontPlanChange;

import java.util.List;

@Configuration
public class AnnualUpfrontPolicySetsConfiguration {

    @Bean
    public PolicySet<AnnualUpfrontRequestCheck> annualUpfrontRequestPolicies() {
        List<Policy<AnnualUpfrontRequestCheck>> ordered = List.of(
                new AnnualUpfrontEnrollmentActivePolicy(),
                new AnnualUpfrontRequestWindowPolicy(),
                new ScholarshipHolderCannotChooseAnnualUpfrontPolicy(),
                new AnnualUpfrontBlockedByOverdueDebtPolicy(),
                new OnlyOneAnnualUpfrontRequestPolicy(),
                new AnnualUpfrontMonthsStillAvailablePolicy(),
                new AnnualUpfrontLineCountSupportedPolicy(),
                new AnnualUpfrontPriceAvailablePolicy());
        return PolicySet.named("ANNUAL_UPFRONT_REQUEST", ordered);
    }

    @Bean
    public PolicySet<AnnualUpfrontPlanChange> annualUpfrontPlanChangePolicies() {
        return PolicySet.named("ANNUAL_UPFRONT_PLAN_CHANGE", List.of(new AnnualUpfrontPlanCannotBeChangedPolicy()));
    }

    @Bean
    public PolicySet<AnnualUpfrontIdempotencyKey> annualUpfrontIdempotencyKeyPolicies() {
        return PolicySet.named("ANNUAL_UPFRONT_IDEMPOTENCY_KEY", List.of(new AnnualUpfrontIdempotencyKeyPolicy()));
    }

    @Bean
    public PolicySet<AnnualUpfrontTermsAcceptance> annualUpfrontTermsPolicies() {
        return PolicySet.named("ANNUAL_UPFRONT_TERMS", List.of(new AnnualUpfrontTermsAcceptedPolicy()));
    }
}
