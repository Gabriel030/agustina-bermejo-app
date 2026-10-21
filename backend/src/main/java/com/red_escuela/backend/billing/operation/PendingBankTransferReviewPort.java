package com.red_escuela.backend.billing.operation;

public interface PendingBankTransferReviewPort {

    void approvePendingPayment(Long paymentId, Long reviewerPersonId);

    void rejectPendingPayment(Long paymentId, String rejectionReason, Long reviewerPersonId);
}
