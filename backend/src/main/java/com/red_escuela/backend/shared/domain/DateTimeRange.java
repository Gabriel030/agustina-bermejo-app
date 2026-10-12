package com.red_escuela.backend.shared.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public record DateTimeRange(LocalDateTime start, LocalDateTime end) {

    public DateTimeRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new InvalidDateRangeException(start, end);
        }
    }

    public boolean contains(LocalDateTime instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(start) && !instant.isAfter(end);
    }
}
