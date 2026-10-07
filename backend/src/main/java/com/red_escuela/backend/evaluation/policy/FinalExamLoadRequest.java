package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;

public record FinalExamLoadRequest(Long enrollmentId, EnrollmentStatus enrollmentStatus) {
}
