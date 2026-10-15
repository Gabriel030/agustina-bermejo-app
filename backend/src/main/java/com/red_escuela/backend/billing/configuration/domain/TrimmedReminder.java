package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalDate;

public record TrimmedReminder(String noticeKey, LocalDate date, TrimReason reason) {

    public enum TrimReason {
        MIN_SPACING,
        MONTHLY_CAP,
        OUT_OF_WINDOW
    }
}
