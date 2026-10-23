package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.domain.PaymentMethodType;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundRequestResult;
import com.red_escuela.backend.billing.payment.operation.CompleteRefundCommand;
import com.red_escuela.backend.billing.payment.operation.CompleteRefundOperation;
import com.red_escuela.backend.billing.payment.operation.FailRefundCommand;
import com.red_escuela.backend.billing.payment.operation.FailRefundOperation;
import com.red_escuela.backend.billing.payment.operation.IssueRefundCommand;
import com.red_escuela.backend.billing.payment.operation.IssueRefundOperation;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class RefundOperationRunner {
    private final BillingOperationPipeline pipeline;
    private final IssueRefundOperation issueOperation;
    private final CompleteRefundOperation completeOperation;
    private final FailRefundOperation failOperation;
    private final RefundRepository refunds;
    private final PaymentActorResolver actorResolver;

    public RefundOperationRunner(BillingOperationPipeline pipeline, IssueRefundOperation issueOperation, CompleteRefundOperation completeOperation,
                                 FailRefundOperation failOperation, RefundRepository refunds, PaymentActorResolver actorResolver) {
        this.pipeline = pipeline;
        this.issueOperation = issueOperation;
        this.completeOperation = completeOperation;
        this.failOperation = failOperation;
        this.refunds = refunds;
        this.actorResolver = actorResolver;
    }

    public RefundRequestResult runRefundRequest(Long studentId, Money amount, String destinationAccount, String destinationHolder, boolean requestedByStudent) {
        IssueRefundCommand command = new IssueRefundCommand(studentId, amount, destinationAccount, destinationHolder, actorResolver.currentPersonAsActor().personId(),
                !requestedByStudent && actorResolver.currentActorIsDirector(), null);
        return switch (pipeline.runOperation(issueOperation, command)) {
            case BillingOperationOutcome.Processed<List<Refund>> processed -> new RefundRequestResult(null, processed.result());
            case BillingOperationOutcome.AwaitingAuthorization<List<Refund>> awaiting ->
                    new RefundRequestResult(awaiting.authorizationRequestId(), refunds.findByAuthorizationRequestIdOrderByIdAsc(awaiting.authorizationRequestId()));
        };
    }

    public Refund runRefundCompletion(Long refundId, LocalDate completedOn, String reference, PaymentMethodType methodType) {
        return pipeline.runOperation(completeOperation, new CompleteRefundCommand(refundId, completedOn, reference, methodType, actorResolver.currentPersonAsActor()))
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }

    public Refund runRefundFailure(Long refundId, String reason) {
        return pipeline.runOperation(failOperation, new FailRefundCommand(refundId, reason, actorResolver.currentPersonAsActor()))
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
    }
}
