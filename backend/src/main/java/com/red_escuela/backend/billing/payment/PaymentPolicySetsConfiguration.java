package com.red_escuela.backend.billing.payment;

import com.red_escuela.backend.billing.payment.policy.PaymentGatewayAvailabilityContext;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayAvailabilityPolicy;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayCapabilityContext;
import com.red_escuela.backend.billing.payment.policy.PaymentGatewayCapabilityPolicy;
import com.red_escuela.backend.billing.payment.policy.IdempotencyKeyReuseConsistencyPolicy;
import com.red_escuela.backend.billing.payment.policy.IdempotencyKeyReuseContext;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PaymentPolicySetsConfiguration {

    @Bean
    public PolicySet<PaymentGatewayAvailabilityContext> paymentGatewayAvailabilityPolicySet() {
        return PolicySet.named("PAYMENT_GATEWAY_RESOLUTION", List.of(new PaymentGatewayAvailabilityPolicy()));
    }

    @Bean
    public PolicySet<PaymentGatewayCapabilityContext> paymentGatewayCapabilityPolicySet() {
        return PolicySet.named("PAYMENT_GATEWAY_CAPABILITY", List.of(new PaymentGatewayCapabilityPolicy()));
    }

    @Bean
    public PolicySet<IdempotencyKeyReuseContext> idempotencyKeyReusePolicySet() {
        return PolicySet.named("IDEMPOTENCY_KEY_REUSE", List.of(new IdempotencyKeyReuseConsistencyPolicy()));
    }
}
