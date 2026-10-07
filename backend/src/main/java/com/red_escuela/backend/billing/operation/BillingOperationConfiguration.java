package com.red_escuela.backend.billing.operation;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BillingOperationConfiguration {

    @Bean
    public OperationHandlerRegistry operationHandlerRegistry(ObjectProvider<OperationPreHandlers> registrations) {
        return new OperationHandlerRegistry(registrations.orderedStream().toList());
    }

    @Bean
    public BillingOperations billingOperations(ObjectProvider<BillingOperation<?, ?>> operations) {
        return new BillingOperations(operations.orderedStream().toList());
    }

    @Bean
    public BillingOperationPipeline billingOperationPipeline(OperationHandlerRegistry handlerRegistry, AuthorizationRequestPort authorizationRequests,
                                                             ApplicationEventPublisher eventPublisher) {
        return new BillingOperationPipeline(handlerRegistry, authorizationRequests, eventPublisher);
    }
}
