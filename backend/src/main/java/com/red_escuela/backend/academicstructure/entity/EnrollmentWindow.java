package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.shared.domain.InvalidDateRangeException;

import java.time.LocalDateTime;
import java.util.Objects;

public record EnrollmentWindow(LocalDateTime start, LocalDateTime end) {

    public EnrollmentWindow {
        if (start != null && end != null && end.isBefore(start)) {
            throw new InvalidDateRangeException(start, end);
        }
    }

    public static EnrollmentWindow unbounded() {
        return new EnrollmentWindow(null, null);
    }

    public boolean isOpenAt(LocalDateTime moment) {
        return isOpenAt(moment, null);
    }

    public boolean isOpenAt(LocalDateTime moment, TermEnrollmentOverride override) {
        Objects.requireNonNull(moment, "moment");
        if (override == TermEnrollmentOverride.CLOSED) {
            return false;
        }
        if (override == TermEnrollmentOverride.OPEN) {
            return true;
        }
        return (start == null || !moment.isBefore(start)) && (end == null || !moment.isAfter(end));
    }
}
