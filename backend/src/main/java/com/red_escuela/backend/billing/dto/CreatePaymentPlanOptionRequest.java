package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreatePaymentPlanOptionRequest(
        @NotBlank String description, @NotNull PaymentPlanCadence cadence, MonetaryAdjustmentRequest upfrontDiscount,
        boolean upfrontWindowEnabled, LocalDate upfrontWindowStart, LocalDate upfrontWindowEnd) {
}
