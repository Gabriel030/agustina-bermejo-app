package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.shared.domain.Percentage;

public record ActiveEnrollmentLimitRow(Long enrollmentId, Long studentId, Percentage maxAbsenceRate) {
}
