package com.red_escuela.backend.billing.run;

import com.red_escuela.backend.billing.run.policy.InconsistencyStaysLocalContext;
import com.red_escuela.backend.billing.run.policy.ManualIssuanceRequiresSimulationContext;
import com.red_escuela.backend.billing.run.policy.ManualIssuanceRequiresSimulationPolicy;
import com.red_escuela.backend.billing.run.policy.ManualIssuanceWithinCurrentMonthContext;
import com.red_escuela.backend.billing.run.policy.ManualIssuanceWithinCurrentMonthPolicy;
import com.red_escuela.backend.billing.run.policy.InconsistencyStaysLocalPolicy;
import com.red_escuela.backend.billing.run.policy.NightlyBillingCompletedTodayContext;
import com.red_escuela.backend.billing.run.policy.NightlyBillingCompletedTodayPolicy;
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

    @Bean
    public PolicySet<NightlyBillingCompletedTodayContext> nightlyBillingCompletedTodayPolicySet() {
        return PolicySet.named("NIGHTLY_BILLING_COMPLETED_TODAY", List.of(new NightlyBillingCompletedTodayPolicy()));
    }

    @Bean
    public PolicySet<ManualIssuanceWithinCurrentMonthContext> manualIssuanceWithinCurrentMonthPolicySet() {
        return PolicySet.named("MANUAL_ISSUANCE_WITHIN_CURRENT_MONTH", List.of(new ManualIssuanceWithinCurrentMonthPolicy()));
    }

    @Bean
    public PolicySet<ManualIssuanceRequiresSimulationContext> manualIssuanceRequiresSimulationPolicySet() {
        return PolicySet.named("MANUAL_ISSUANCE_REQUIRES_SIMULATION", List.of(new ManualIssuanceRequiresSimulationPolicy()));
    }
}
