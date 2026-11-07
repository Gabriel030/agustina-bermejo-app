package com.red_escuela.backend.billing.advanceenrollmentfee;

import com.red_escuela.backend.billing.advanceenrollmentfee.policy.AdvanceEnrollmentFeeFacts;
import com.red_escuela.backend.billing.advanceenrollmentfee.policy.AdvanceFeeEnrollmentActivePolicy;
import com.red_escuela.backend.billing.advanceenrollmentfee.policy.AdvanceFeeNextYearConfiguredPolicy;
import com.red_escuela.backend.billing.advanceenrollmentfee.policy.AdvanceFeeNoOverdueDebtPolicy;
import com.red_escuela.backend.billing.advanceenrollmentfee.policy.AdvanceFeeNotAlreadyIssuedPolicy;
import com.red_escuela.backend.billing.advanceenrollmentfee.policy.AdvanceFeeWindowNotClosedPolicy;
import com.red_escuela.backend.billing.advanceenrollmentfee.policy.AdvanceFeeWindowOpenedPolicy;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AdvanceEnrollmentFeePolicySetsConfiguration {

    @Bean
    public PolicySet<AdvanceEnrollmentFeeFacts> advanceEnrollmentFeeEligibilityPolicySet() {
        List<Policy<AdvanceEnrollmentFeeFacts>> ordered = List.of(
                new AdvanceFeeNextYearConfiguredPolicy(),
                new AdvanceFeeEnrollmentActivePolicy(),
                new AdvanceFeeNotAlreadyIssuedPolicy(),
                new AdvanceFeeNoOverdueDebtPolicy(),
                new AdvanceFeeWindowOpenedPolicy(),
                new AdvanceFeeWindowNotClosedPolicy());
        return PolicySet.named("ADVANCE_ENROLLMENT_FEE_ELIGIBILITY", ordered);
    }
}
