package com.red_escuela.backend.enrollment.usecase.equivalence;

public record VoidSubjectEquivalenceCommand(Long programEnrollmentId, Long equivalenceId, String reason) {
}
