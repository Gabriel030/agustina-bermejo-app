package com.red_escuela.backend.notification.delivery;

import com.red_escuela.backend.notification.delivery.policy.EmailDeliveryContext;
import com.red_escuela.backend.notification.delivery.policy.PreDueEmailOptOutPolicy;
import com.red_escuela.backend.notification.delivery.policy.VerifiedEmailRequiredPolicy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class NotificationDeliveryPolicySetsConfiguration {

    @Bean
    public PolicySet<EmailDeliveryContext> emailDeliveryPolicySet() {
        return PolicySet.named("EMAIL_DELIVERY", List.of(new PreDueEmailOptOutPolicy(), new VerifiedEmailRequiredPolicy()));
    }
}
