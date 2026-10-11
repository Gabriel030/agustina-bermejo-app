package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.events.LateFeeCutoffKind;

import java.time.LocalDate;
import java.util.Objects;

public record LateFeeCutoff(LateFeeCutoffKind kind, LocalDate lastDay) {

    public LateFeeCutoff {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(lastDay, "lastDay");
    }

    public static LateFeeCutoff closingOfMonthBefore(LocalDate businessDate) {
        return new LateFeeCutoff(LateFeeCutoffKind.MONTH_CLOSE, businessDate.withDayOfMonth(1).minusDays(1));
    }
}
