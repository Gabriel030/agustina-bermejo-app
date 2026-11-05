package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.PaymentMethodType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CompleteRefundRequest(@NotNull LocalDate completedOn, String reference, @NotNull PaymentMethodType methodType) {
}
