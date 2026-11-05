package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.dto.RegisterCashPaymentRequest;
import com.red_escuela.backend.billing.payment.domain.CashPaymentRegistrationResult;
import com.red_escuela.backend.billing.payment.operation.RegisterCashPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.RegisterCashPaymentOperation;
import org.springframework.stereotype.Component;

@Component
public class CashPaymentOperationRunner {
    private final BillingOperationPipeline pipeline;
    private final RegisterCashPaymentOperation operation;
    private final PaymentActorResolver actorResolver;

    public CashPaymentOperationRunner(BillingOperationPipeline pipeline, RegisterCashPaymentOperation operation, PaymentActorResolver actorResolver) {
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
    }

    public CashPaymentRegistrationResult runRegistration(Long studentId, RegisterCashPaymentRequest request, String idempotencyKey) {
        RegisterCashPaymentCommand command = new RegisterCashPaymentCommand(studentId, request.amount(), request.paidOn(), request.receiptNumber(),
                request.backdateReason(), request.previewFingerprint(), actorResolver.currentActorIsDirector(), idempotencyKey,
                actorResolver.currentPersonAsActor());
        return pipeline.runOperation(operation, command).resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }
}
