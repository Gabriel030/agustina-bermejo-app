package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.shared.domain.Grade;

public record CourseworkEntry(EnrollmentStatus status, Grade finalScore) {
}
