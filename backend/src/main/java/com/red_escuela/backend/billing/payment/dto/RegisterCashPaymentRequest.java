package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.shared.domain.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RegisterCashPaymentRequest(@NotNull Money amount, LocalDate paidOn, String receiptNumber, String backdateReason, @NotBlank String previewFingerprint) {
}
