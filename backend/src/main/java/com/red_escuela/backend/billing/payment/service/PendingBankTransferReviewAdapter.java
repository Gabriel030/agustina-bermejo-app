package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.operation.PendingBankTransferReviewPort;
import com.red_escuela.backend.billing.operation.PendingTransferApprovalAssessment;
import com.red_escuela.backend.billing.operation.PendingTransferApprovalOutcome;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PendingBankTransferDecisionResult;
import com.red_escuela.backend.billing.payment.operation.ApprovePendingBankTransferPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.ApprovePendingBankTransferPaymentOperation;
import com.red_escuela.backend.billing.payment.operation.RejectPendingBankTransferPaymentCommand;
import com.red_escuela.backend.billing.payment.operation.RejectPendingBankTransferPaymentOperation;
import com.red_escuela.backend.billing.payment.policy.PaymentReviewRejections;
import com.red_escuela.backend.billing.payment.policy.PaymentVerificationCheck;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class PendingBankTransferReviewAdapter implements PendingBankTransferReviewPort {
    private final BillingOperationPipeline pipeline;
    private final ApprovePendingBankTransferPaymentOperation approveOperation;
    private final RejectPendingBankTransferPaymentOperation rejectOperation;
    private final PaymentRepository payments;
    private final PolicySet<PaymentVerificationCheck> verificationPolicies;
    private final Clock clock;

    public PendingBankTransferReviewAdapter(BillingOperationPipeline pipeline, ApprovePendingBankTransferPaymentOperation approveOperation,
                                            RejectPendingBankTransferPaymentOperation rejectOperation, PaymentRepository payments,
                                            PolicySet<PaymentVerificationCheck> paymentVerificationPolicySet, Clock clock) {
        this.pipeline = pipeline;
        this.approveOperation = approveOperation;
        this.rejectOperation = rejectOperation;
        this.payments = payments;
        this.verificationPolicies = paymentVerificationPolicySet;
        this.clock = clock;
    }

    @Override
    public PendingTransferApprovalAssessment assessApproval(Long paymentId, PaymentVerification verification) {
        Payment payment = payments.findById(paymentId).orElseThrow();
        verificationPolicies.evaluateRulesUntilFirstViolation(ApprovePendingBankTransferPaymentOperation.verificationCheckOf(payment, verification, LocalDate.now(clock)))
                .throwIfDenied(PaymentReviewRejections::rejectionOf);
        return new PendingTransferApprovalAssessment(payment.getStartedAt().toLocalDate(),
                ApprovePendingBankTransferPaymentOperation.verifiedTransferDateOf(payment, verification));
    }

    @Override
    public PendingTransferApprovalOutcome approvePendingPayment(Long paymentId, Long reviewerPersonId, PaymentVerification verification) {
        PendingBankTransferDecisionResult result = pipeline.runOperation(approveOperation,
                        new ApprovePendingBankTransferPaymentCommand(paymentId, StatusChangeActor.ofPerson(reviewerPersonId), verification))
                .resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException("Unexpected authorization request " + requestId));
        return new PendingTransferApprovalOutcome(result.paymentId(), result.receiptItemInvoiceIds(), result.invoicesSettled(), result.invoicesReleasedFromReview());
    }

    @Override
    public List<Long> rejectPendingPayment(Long paymentId, String rejectionReason, Long reviewerPersonId) {
        return pipeline.runOperation(rejectOperation, new RejectPendingBankTransferPaymentCommand(paymentId, rejectionReason, StatusChangeActor.ofPerson(reviewerPersonId)))
                .resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException("Unexpected authorization request " + requestId)).receiptItemInvoiceIds();
    }
}
