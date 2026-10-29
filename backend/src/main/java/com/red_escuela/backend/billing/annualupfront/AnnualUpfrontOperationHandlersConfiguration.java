package com.red_escuela.backend.billing.annualupfront;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.annualupfront.service.RegisteredAnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.service.RequestAnnualUpfrontCommand;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.OperationPreHandlers;
import com.red_escuela.backend.billing.payment.operation.IdempotencyKeyHandler;
import com.red_escuela.backend.billing.payment.service.IdempotencyRecordService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AnnualUpfrontOperationHandlersConfiguration {

    @Bean
    public OperationPreHandlers requestAnnualUpfrontPreHandlers(IdempotencyRecordService recordService, ObjectMapper objectMapper) {
        return new OperationPreHandlers(BillingOperationType.REQUEST_ANNUAL_UPFRONT,
                List.of(new IdempotencyKeyHandler<RequestAnnualUpfrontCommand>(recordService, objectMapper, RegisteredAnnualUpfrontRequest.class)));
    }
}
