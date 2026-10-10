package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record LateFeeSchedule(LocalDate originDueDate, int gracePeriodDays) {

    public LateFeeSchedule {
        Objects.requireNonNull(originDueDate, "originDueDate");
        if (gracePeriodDays < 0) {
            throw new IllegalArgumentException("gracePeriodDays must not be negative");
        }
    }

    public LocalDate firstLateFeeDate() {
        return originDueDate.plusDays(gracePeriodDays + 1L);
    }

    public LateFeeMonth lateFeeMonth(int ordinal) {
        if (ordinal < 1) {
            throw new IllegalArgumentException("The late fee month ordinal starts at 1");
        }
        return new LateFeeMonth(ordinal, firstLateFeeDate().plusMonths(ordinal - 1L));
    }

    public List<LateFeeMonth> monthsWithFactDateOnOrBefore(LocalDate limit) {
        Objects.requireNonNull(limit, "limit");
        List<LateFeeMonth> months = new ArrayList<>();
        int ordinal = 1;
        while (!lateFeeMonth(ordinal).factDate().isAfter(limit)) {
            months.add(lateFeeMonth(ordinal));
            ordinal++;
        }
        return months;
    }
}
