package com.red_escuela.backend.enrollment.api;

public record GraduationCandidateView(
        Long enrollmentId,
        Long studentId,
        String fullName,
        String documentTypeLabel,
        String documentNumber,
        String titleName) {
}
