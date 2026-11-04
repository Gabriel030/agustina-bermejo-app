package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.shared.domain.TermEnrollmentWindowRequiredException;
import com.red_escuela.backend.shared.domain.InvalidDateRangeException;

import java.time.LocalDateTime;
import java.util.Objects;

public record EnrollmentWindow(LocalDateTime start, LocalDateTime end) {

    public EnrollmentWindow {
        if (start == null || end == null) {
            throw new TermEnrollmentWindowRequiredException();
        }
        if (end.isBefore(start)) {
            throw new InvalidDateRangeException(start, end);
        }
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
        return !moment.isBefore(start) && !moment.isAfter(end);
    }
}
