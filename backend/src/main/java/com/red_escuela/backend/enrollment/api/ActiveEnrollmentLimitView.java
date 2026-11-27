package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.domain.Percentage;

public record ActiveEnrollmentLimitView(Long enrollmentId, Long studentId, Percentage maxAbsenceRate) {
}
