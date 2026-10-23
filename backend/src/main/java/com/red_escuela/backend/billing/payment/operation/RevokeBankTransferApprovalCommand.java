package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Objects;

public record RevokeBankTransferApprovalCommand(Long paymentId, String reason, StatusChangeActor reviewer) {

    public RevokeBankTransferApprovalCommand {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(reviewer, "reviewer");
    }
}
