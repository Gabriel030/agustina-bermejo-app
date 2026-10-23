package com.red_escuela.backend.billing.reporting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.YearMonth;
import java.util.Optional;

public record FirstBillingMonthResponse(@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^\\d{4}-\\d{2}$") String month) {

    public static FirstBillingMonthResponse from(Optional<YearMonth> month) {
        return new FirstBillingMonthResponse(month.map(YearMonth::toString).orElse(null));
    }
}
