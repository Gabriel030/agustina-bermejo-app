package com.red_escuela.backend.attendance.repository;

public interface EnrollmentAttendanceCounts {
    Long getEnrollmentId();

    Long getTotal();

    Long getPresent();

    Long getAbsences();
}
