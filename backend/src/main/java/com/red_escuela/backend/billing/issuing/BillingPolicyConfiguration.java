package com.red_escuela.backend.billing.issuing;

import com.red_escuela.backend.billing.issuing.policy.DueDateNotBeforeIssueDatePolicy;
import com.red_escuela.backend.billing.issuing.policy.EnrollmentFeeSlotRequiredPolicy;
import com.red_escuela.backend.billing.issuing.policy.NotAlreadyIssuedPolicy;
import com.red_escuela.backend.billing.issuing.policy.OfferingCurrencyPolicy;
import com.red_escuela.backend.billing.issuing.policy.ReasonFeeTypePolicy;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BillingPolicyConfiguration {

    @Bean
    public PolicySet<IssuanceContext> issuancePolicySet() {
        List<Policy<IssuanceContext>> ordered = List.of(
                new ReasonFeeTypePolicy(),
                new DueDateNotBeforeIssueDatePolicy(),
                new EnrollmentFeeSlotRequiredPolicy(),
                new OfferingCurrencyPolicy(),
                new NotAlreadyIssuedPolicy());
        return PolicySet.of(ordered);
    }
}
