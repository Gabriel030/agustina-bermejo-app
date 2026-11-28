package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.shared.domain.Grade;

import java.time.LocalDate;

public record CourseworkEntry(EnrollmentStatus status, Grade finalScore, Long enrollmentId, LocalDate finalScoreDate) {

    public CourseworkEntry(EnrollmentStatus status, Grade finalScore, Long enrollmentId) {
        this(status, finalScore, enrollmentId, null);
    }
}
