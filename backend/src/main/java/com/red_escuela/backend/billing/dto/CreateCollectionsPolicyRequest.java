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
        // null = rige desde hoy. Puede ser una fecha futura — una política cargada hoy con
        // vigencia dentro de un mes no afecta nada hasta esa fecha (test 70).
        LocalDate effectiveFrom) {
}
