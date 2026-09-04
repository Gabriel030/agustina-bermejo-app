package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.PaymentPlanCadence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreatePaymentPlanOptionRequest(
        @NotBlank String description, @NotNull PaymentPlanCadence cadence, MonetaryAdjustmentValueRequest upfrontDiscount,
        boolean upfrontWindowEnabled, LocalDate upfrontWindowStart, LocalDate upfrontWindowEnd) {
}
