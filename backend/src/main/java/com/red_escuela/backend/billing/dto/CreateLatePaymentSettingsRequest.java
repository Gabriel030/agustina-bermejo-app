package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.NotNull;

import com.red_escuela.backend.shared.domain.Percentage;
import java.time.LocalDate;

public record CreateLatePaymentSettingsRequest(
        @NotNull Integer gracePeriodDays,
        @NotNull Percentage dailyLateFeeRatePercent,
        @NotNull Integer suspensionAfterDays,
        @NotNull Integer suspensionAfterInvoiceCount,
        LocalDate effectiveFrom) {
}
