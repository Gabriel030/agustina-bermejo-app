package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.PendingBankTransferReviewPort;
import com.red_escuela.backend.billing.payment.operation.ApprovePendingBankTransferPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.ApprovePendingBankTransferPaymentOperation;
import com.red_escuela.backend.billing.payment.operation.RejectPendingBankTransferPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.RejectPendingBankTransferPaymentOperation;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Component;

@Component
public class PendingBankTransferReviewAdapter implements PendingBankTransferReviewPort {
    private final BillingOperationPipeline pipeline;
    private final ApprovePendingBankTransferPaymentOperation approveOperation;
    private final RejectPendingBankTransferPaymentOperation rejectOperation;

    public PendingBankTransferReviewAdapter(BillingOperationPipeline pipeline, ApprovePendingBankTransferPaymentOperation approveOperation,
                                            RejectPendingBankTransferPaymentOperation rejectOperation) {
        this.pipeline = pipeline;
        this.approveOperation = approveOperation;
        this.rejectOperation = rejectOperation;
    }

    @Override
    public void approvePendingPayment(Long paymentId, Long reviewerPersonId) {
        pipeline.runOperation(approveOperation, new ApprovePendingBankTransferPaymentCommand(paymentId, StatusChangeActor.ofPerson(reviewerPersonId)))
                .resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException("Unexpected authorization request " + requestId));
    }

    @Override
    public void rejectPendingPayment(Long paymentId, String rejectionReason, Long reviewerPersonId) {
        pipeline.runOperation(rejectOperation, new RejectPendingBankTransferPaymentCommand(paymentId, rejectionReason, StatusChangeActor.ofPerson(reviewerPersonId)))
                .resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException("Unexpected authorization request " + requestId));
    }
}
