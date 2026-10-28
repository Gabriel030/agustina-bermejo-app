package com.red_escuela.backend.billing.annualupfront;

import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontBlockedByOverdueDebtPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontLineCountSupportedPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontMonthsStillAvailablePolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontPriceAvailablePolicy;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontRequestCheck;
import com.red_escuela.backend.billing.annualupfront.policy.AnnualUpfrontRequestWindowPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.OnlyOneAnnualUpfrontRequestPolicy;
import com.red_escuela.backend.billing.annualupfront.policy.ScholarshipHolderCannotChooseAnnualUpfrontPolicy;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AnnualUpfrontPolicySetsConfiguration {

    @Bean
    public PolicySet<AnnualUpfrontRequestCheck> annualUpfrontRequestPolicies() {
        List<Policy<AnnualUpfrontRequestCheck>> ordered = List.of(
                new AnnualUpfrontRequestWindowPolicy(),
                new ScholarshipHolderCannotChooseAnnualUpfrontPolicy(),
                new AnnualUpfrontBlockedByOverdueDebtPolicy(),
                new OnlyOneAnnualUpfrontRequestPolicy(),
                new AnnualUpfrontMonthsStillAvailablePolicy(),
                new AnnualUpfrontLineCountSupportedPolicy(),
                new AnnualUpfrontPriceAvailablePolicy());
        return PolicySet.named("ANNUAL_UPFRONT_REQUEST", ordered);
    }
}
