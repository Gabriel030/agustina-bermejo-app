package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.NotNull;

import com.red_escuela.backend.shared.domain.Percentage;
import java.time.LocalDate;

public record CreateCollectionsPolicyRequest(
        @NotNull Integer gracePeriodDays,
        @NotNull Percentage surchargePercent,
        @NotNull Integer suspensionAfterDays,
        @NotNull Integer suspensionAfterInvoiceCount,
        @NotNull Integer withdrawalAfterDays,
        @NotNull Integer withdrawalAfterInvoiceCount,
        LocalDate effectiveFrom) {
}
