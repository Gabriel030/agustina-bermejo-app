package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public record LateFeeFreeze(Optional<LocalDate> enrollmentEndDate, boolean frozenByRuleOfTheEndDate) {

    public LateFeeFreeze {
        Objects.requireNonNull(enrollmentEndDate, "enrollmentEndDate");
    }

    public Predicate<LocalDate> frozenOn() {
        return day -> frozenByRuleOfTheEndDate && enrollmentEndDate.map(day::isAfter).orElse(false);
    }
}
