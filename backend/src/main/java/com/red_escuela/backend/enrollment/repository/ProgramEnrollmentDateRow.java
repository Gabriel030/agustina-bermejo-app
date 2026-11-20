package com.red_escuela.backend.enrollment.repository;

import java.time.LocalDateTime;

public record ProgramEnrollmentDateRow(Long enrollmentId, Long studentId, Long programId, LocalDateTime enrollmentDate) {
}
