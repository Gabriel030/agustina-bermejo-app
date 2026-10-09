package com.red_escuela.backend.billing.collections;

import com.red_escuela.backend.billing.collections.policy.InvoiceIsPastDueDatePolicy;
import com.red_escuela.backend.billing.collections.policy.OverdueDetectionContext;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CollectionsPolicySetsConfiguration {

    @Bean
    public PolicySet<OverdueDetectionContext> overdueDetectionPolicySet() {
        return PolicySet.named("OVERDUE_DETECTION", List.of(new InvoiceIsPastDueDatePolicy()));
    }
}
