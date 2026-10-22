package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Objects;

public record ApprovePendingBankTransferPaymentCommand(Long paymentId, StatusChangeActor reviewer) {

    public ApprovePendingBankTransferPaymentCommand {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(reviewer, "reviewer");
    }
}
