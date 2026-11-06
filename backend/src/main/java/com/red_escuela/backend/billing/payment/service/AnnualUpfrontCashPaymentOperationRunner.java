package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.domain.AnnualUpfrontCashPaymentResult;
import com.red_escuela.backend.billing.payment.dto.RegisterAnnualUpfrontCashPaymentRequest;
import com.red_escuela.backend.billing.payment.operation.RegisterAnnualUpfrontCashPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.RegisterAnnualUpfrontCashPaymentOperation;
import org.springframework.stereotype.Component;

@Component
public class AnnualUpfrontCashPaymentOperationRunner {
    private final BillingOperationPipeline pipeline;
    private final RegisterAnnualUpfrontCashPaymentOperation operation;
    private final PaymentActorResolver actorResolver;

    public AnnualUpfrontCashPaymentOperationRunner(BillingOperationPipeline pipeline, RegisterAnnualUpfrontCashPaymentOperation operation, PaymentActorResolver actorResolver) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
    }

    public AnnualUpfrontCashPaymentResult run(Long studentId, Long requestId, RegisterAnnualUpfrontCashPaymentRequest request, String idempotencyKey) {
        RegisterAnnualUpfrontCashPaymentCommand command = new RegisterAnnualUpfrontCashPaymentCommand(studentId, requestId, request.amount(), request.receiptNumber(), idempotencyKey,
                actorResolver.currentPersonAsActor());
        return pipeline.runOperation(operation, command).resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }
}
