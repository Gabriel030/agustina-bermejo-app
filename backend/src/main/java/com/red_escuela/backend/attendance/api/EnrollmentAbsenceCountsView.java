package com.red_escuela.backend.attendance.api;

public record EnrollmentAbsenceCountsView(long total, long absent) {

    public static EnrollmentAbsenceCountsView none() {
        return new EnrollmentAbsenceCountsView(0L, 0L);
    }
}
