package com.red_escuela.backend.billing.run;

import com.red_escuela.backend.billing.run.policy.InconsistencyStaysLocalContext;
import com.red_escuela.backend.billing.run.policy.InconsistencyStaysLocalPolicy;
import com.red_escuela.backend.billing.run.policy.PreviousStepAllowsNextPolicy;
import com.red_escuela.backend.billing.run.policy.PreviousStepContext;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BillingRunPolicySetsConfiguration {

    @Bean
    public PolicySet<PreviousStepContext> nightlyBillingStepSequencePolicySet() {
        return PolicySet.named("NIGHTLY_BILLING_STEP_SEQUENCE", List.of(new PreviousStepAllowsNextPolicy()));
    }

    @Bean
    public PolicySet<InconsistencyStaysLocalContext> accountInconsistencyScopePolicySet() {
        return PolicySet.named("ACCOUNT_INCONSISTENCY_SCOPE", List.of(new InconsistencyStaysLocalPolicy()));
    }
}
