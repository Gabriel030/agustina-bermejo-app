package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.policy.BroadcastConsentPolicy;
import com.red_escuela.backend.notification.broadcast.policy.BroadcastDeliveryContext;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BroadcastPolicySetsConfiguration {

    @Bean
    public PolicySet<BroadcastDeliveryContext> broadcastDeliveryPolicySet() {
        return PolicySet.named("BROADCAST_DELIVERY", List.of(new BroadcastConsentPolicy()));
    }
}
