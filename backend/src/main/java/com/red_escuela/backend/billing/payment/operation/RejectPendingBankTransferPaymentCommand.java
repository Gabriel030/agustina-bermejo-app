package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Objects;

public record RejectPendingBankTransferPaymentCommand(Long paymentId, String rejectionReason, StatusChangeActor reviewer) {

    public RejectPendingBankTransferPaymentCommand {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(reviewer, "reviewer");
    }
}
