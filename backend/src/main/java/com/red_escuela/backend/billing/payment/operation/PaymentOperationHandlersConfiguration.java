package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.OperationPreHandlers;
import com.red_escuela.backend.billing.payment.domain.CashPaymentRegistrationResult;
import com.red_escuela.backend.billing.payment.service.IdempotencyRecordService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PaymentOperationHandlersConfiguration {

    @Bean
    public OperationPreHandlers registerPaymentPreHandlers(IdempotencyRecordService recordService, ObjectMapper objectMapper) {
        return new OperationPreHandlers(BillingOperationType.REGISTER_PAYMENT,
                List.of(new IdempotencyKeyHandler<RegisterCashPaymentCommand>(recordService, objectMapper, CashPaymentRegistrationResult.class)));
    }
}
