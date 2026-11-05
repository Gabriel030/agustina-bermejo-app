package com.red_escuela.backend.attendance.api;

public record EnrollmentAbsenceCounts(long total, long absent) {

    public static EnrollmentAbsenceCounts none() {
        return new EnrollmentAbsenceCounts(0L, 0L);
    }
}
