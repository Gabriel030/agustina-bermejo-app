package com.red_escuela.backend.billing.account;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationStrategy;
import com.red_escuela.backend.billing.account.domain.OldestOverdueFirstAllocationStrategy;
import com.red_escuela.backend.billing.account.policy.AllocationPlanContext;
import com.red_escuela.backend.billing.account.policy.AllocationReversalContext;
import com.red_escuela.backend.billing.account.policy.FullInvoicePaymentOnlyPolicy;
import com.red_escuela.backend.billing.account.policy.OldestOverdueFirstAllocationOrderPolicy;
import com.red_escuela.backend.billing.account.policy.PaymentAllocationReversedOnlyOncePolicy;
import com.red_escuela.backend.billing.account.policy.PaymentAllocationSameAccountPolicy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AccountPolicySetsConfiguration {

    @Bean
    public AccountCreditAllocationStrategy accountCreditAllocationStrategy() {
        return new OldestOverdueFirstAllocationStrategy();
    }

    @Bean
    public PolicySet<AllocationPlanContext> paymentAllocationPolicySet() {
        return PolicySet.named("PAYMENT_ALLOCATION", List.of(
                new PaymentAllocationSameAccountPolicy(),
                new FullInvoicePaymentOnlyPolicy(),
                new OldestOverdueFirstAllocationOrderPolicy()));
    }

    @Bean
    public PolicySet<AllocationReversalContext> paymentAllocationReversalPolicySet() {
        return PolicySet.named("PAYMENT_ALLOCATION_REVERSAL", List.of(new PaymentAllocationReversedOnlyOncePolicy()));
    }
}
