package com.red_escuela.backend.billing.invoice;

import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import com.red_escuela.backend.billing.invoice.policy.AnnualUpfrontOfProgramMustUseRequestEndpointPolicy;
import com.red_escuela.backend.billing.invoice.policy.OutstandingDebtBlocksReenrollmentPolicy;
import com.red_escuela.backend.billing.invoice.policy.OutstandingDebtFacts;
import com.red_escuela.backend.billing.invoice.policy.PaymentPlanChoice;

@Configuration
public class InvoicePolicySetsConfiguration {

    @Bean
    public PolicySet<PaymentPlanChoice> paymentPlanSelectionPolicySet() {
        return PolicySet.named("PAYMENT_PLAN_SELECTION", List.of(new AnnualUpfrontOfProgramMustUseRequestEndpointPolicy()));
    }

    @Bean
    public PolicySet<OutstandingDebtFacts> reenrollmentDebtPolicySet() {
        return PolicySet.named("REENROLLMENT_DEBT", List.of(new OutstandingDebtBlocksReenrollmentPolicy()));
    }
}
