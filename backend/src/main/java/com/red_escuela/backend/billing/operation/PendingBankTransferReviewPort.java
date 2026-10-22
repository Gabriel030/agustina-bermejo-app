package com.red_escuela.backend.billing.operation;

public interface PendingBankTransferReviewPort {

    PendingTransferApprovalAssessment assessApproval(Long paymentId, PaymentVerification verification);

    PendingTransferApprovalOutcome approvePendingPayment(Long paymentId, Long reviewerPersonId, PaymentVerification verification);

    void rejectPendingPayment(Long paymentId, String rejectionReason, Long reviewerPersonId);
}
