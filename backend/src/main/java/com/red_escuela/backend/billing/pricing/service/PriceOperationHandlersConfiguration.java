package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.authorization.service.DirectorAuthorizationHandler;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.OperationPreHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PriceOperationHandlersConfiguration {

    @Bean
    public OperationPreHandlers changePricePreHandlers(DirectorAuthorizationHandler directorAuthorizationHandler) {
        return new OperationPreHandlers(BillingOperationType.CHANGE_PRICE, List.of(directorAuthorizationHandler));
    }
}
