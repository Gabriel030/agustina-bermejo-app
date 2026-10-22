package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;

public record ApproveBankTransferReceiptRequest(Money verifiedAmount, LocalDate transferDate, String reviewNote) {

    public PaymentVerification toVerification() {
        return new PaymentVerification(verifiedAmount, transferDate, reviewNote);
    }
}
