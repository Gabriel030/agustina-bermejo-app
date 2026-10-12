package com.red_escuela.backend.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public record DateRange(
        @Column(name = "start_date")
        LocalDate start,
        @Column(name = "end_date")
        LocalDate end) {

    public DateRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new InvalidDateRangeException(start, end);
        }
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public boolean overlaps(DateRange other) {
        Objects.requireNonNull(other, "other");
        return !other.end.isBefore(start) && !other.start.isAfter(end);
    }
}
