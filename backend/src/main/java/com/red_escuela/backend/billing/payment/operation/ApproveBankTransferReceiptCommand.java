package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Objects;

public record ApproveBankTransferReceiptCommand(Long invoiceId, Long studentCurrentAccountId, boolean programAdmission, StatusChangeActor reviewer) {

    public ApproveBankTransferReceiptCommand {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        Objects.requireNonNull(reviewer, "reviewer");
    }
}
