package com.red_escuela.backend.billing.payment.dto;

import jakarta.validation.constraints.Size;

import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.shared.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record ApproveBankTransferReceiptRequest(@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Money verifiedAmount,
                                                @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate transferDate,
                                                @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Size(max = 500) String reviewNote) {

    public PaymentVerification toVerification() {
        return new PaymentVerification(verifiedAmount, transferDate, reviewNote);
    }
}
