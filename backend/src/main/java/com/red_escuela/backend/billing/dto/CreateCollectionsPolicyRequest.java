package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCollectionsPolicyRequest(
        @NotNull Integer gracePeriodDays,
        @NotNull BigDecimal surchargePercentage,
        @NotNull Integer suspensionAfterDays,
        @NotNull Integer suspensionAfterInvoiceCount,
        @NotNull Integer withdrawalAfterDays,
        @NotNull Integer withdrawalAfterInvoiceCount,
        LocalDate effectiveFrom) {
}
