package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;

import java.time.LocalDateTime;

public record AdmissionReceiptSummary(String status, LocalDateTime uploadedAt) {

    public boolean isApproved() {
        return BankTransferReceiptStatus.APPROVED.name().equals(status)
                || BankTransferReceiptStatus.APPLIED_TO_DEBT.name().equals(status);
    }
}
