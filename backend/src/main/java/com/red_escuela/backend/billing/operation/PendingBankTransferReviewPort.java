package com.red_escuela.backend.billing.operation;

import java.util.List;

public interface PendingBankTransferReviewPort {

    PendingTransferApprovalAssessment assessApproval(Long paymentId, PaymentVerification verification);

    PendingTransferApprovalOutcome approvePendingPayment(Long paymentId, Long reviewerPersonId, PaymentVerification verification);

    List<Long> rejectPendingPayment(Long paymentId, String rejectionReason, Long reviewerPersonId);
}
