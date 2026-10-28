package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;
import com.red_escuela.backend.billing.payment.operation.RegisterChargebackCommand;
import com.red_escuela.backend.billing.payment.operation.RegisterChargebackOperation;
import com.red_escuela.backend.billing.payment.operation.ReverseChargebackCommand;
import com.red_escuela.backend.billing.payment.operation.ReverseChargebackOperation;
import com.red_escuela.backend.billing.payment.operation.RevokeBankTransferApprovalCommand;
import com.red_escuela.backend.billing.payment.operation.RevokeBankTransferApprovalOperation;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class PaymentReversalOperationRunner {
    private final BillingOperationPipeline pipeline;
    private final RevokeBankTransferApprovalOperation revocationOperation;
    private final RegisterChargebackOperation chargebackOperation;
    private final ReverseChargebackOperation chargebackReversalOperation;
    private final PaymentActorResolver actorResolver;

    public PaymentReversalOperationRunner(BillingOperationPipeline pipeline, RevokeBankTransferApprovalOperation revocationOperation,
                                          RegisterChargebackOperation chargebackOperation, ReverseChargebackOperation chargebackReversalOperation,
                                          PaymentActorResolver actorResolver) {
        this.pipeline = pipeline;
        this.revocationOperation = revocationOperation;
        this.chargebackOperation = chargebackOperation;
        this.chargebackReversalOperation = chargebackReversalOperation;
        this.actorResolver = actorResolver;
    }

    public PaymentReversalResult runRevocation(Long paymentId, String reason) {
        return pipeline.runOperation(revocationOperation, new RevokeBankTransferApprovalCommand(paymentId, reason, actorResolver.currentPersonAsActor()))
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }

    public PaymentReversalResult runChargebackRegistration(Long paymentId, Money amount, LocalDate chargedBackOn, String reference) {
        return pipeline.runOperation(chargebackOperation, new RegisterChargebackCommand(paymentId, amount, chargedBackOn, reference, actorResolver.currentPersonAsActor()))
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }

    public PaymentReversalResult runChargebackReversal(Long chargebackId) {
        return pipeline.runOperation(chargebackReversalOperation, new ReverseChargebackCommand(chargebackId, actorResolver.currentPersonAsActor()))
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }
}
