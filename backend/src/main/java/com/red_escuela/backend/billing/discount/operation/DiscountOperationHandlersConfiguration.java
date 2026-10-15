package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.authorization.service.DirectorAuthorizationHandler;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.OperationPreHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DiscountOperationHandlersConfiguration {

    @Bean
    public OperationPreHandlers createDiscountDefinitionPreHandlers(DirectorAuthorizationHandler directorAuthorizationHandler) {
        return new OperationPreHandlers(BillingOperationType.CREATE_DISCOUNT_DEFINITION, List.of(directorAuthorizationHandler));
    }

    @Bean
    public OperationPreHandlers closeDiscountDefinitionPreHandlers(DirectorAuthorizationHandler directorAuthorizationHandler) {
        return new OperationPreHandlers(BillingOperationType.CLOSE_DISCOUNT_DEFINITION, List.of(directorAuthorizationHandler));
    }

    @Bean
    public OperationPreHandlers grantStudentDiscountPreHandlers(DirectorAuthorizationHandler directorAuthorizationHandler) {
        return new OperationPreHandlers(BillingOperationType.GRANT_STUDENT_DISCOUNT, List.of(directorAuthorizationHandler));
    }

    @Bean
    public OperationPreHandlers revokeStudentDiscountPreHandlers(DirectorAuthorizationHandler directorAuthorizationHandler) {
        return new OperationPreHandlers(BillingOperationType.REVOKE_STUDENT_DISCOUNT, List.of(directorAuthorizationHandler));
    }
}
