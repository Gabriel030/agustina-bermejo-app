package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class LateFeeCorrectionFiscalWindow {
    public static final long WINDOW_DAYS = 15;

    private LateFeeCorrectionFiscalWindow() {
    }

    public static boolean isOutside(LocalDate factRegisteredOn, LocalDate creditNoteIssueDate) {
        Objects.requireNonNull(factRegisteredOn, "factRegisteredOn");
        Objects.requireNonNull(creditNoteIssueDate, "creditNoteIssueDate");
        return ChronoUnit.DAYS.between(factRegisteredOn, creditNoteIssueDate) > WINDOW_DAYS;
    }
}
