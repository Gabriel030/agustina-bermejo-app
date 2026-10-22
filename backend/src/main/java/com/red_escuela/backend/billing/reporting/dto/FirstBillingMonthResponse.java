package com.red_escuela.backend.billing.reporting.dto;

import java.time.YearMonth;
import java.util.Optional;

public record FirstBillingMonthResponse(String month) {

    public static FirstBillingMonthResponse from(Optional<YearMonth> month) {
        return new FirstBillingMonthResponse(month.map(YearMonth::toString).orElse(null));
    }
}
