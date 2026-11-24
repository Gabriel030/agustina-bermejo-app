package com.red_escuela.backend.enrollment.usecase.equivalence;

import com.red_escuela.backend.enrollment.dto.RecordSubjectEquivalenceRequest;

public record RecordSubjectEquivalenceCommand(Long programEnrollmentId, RecordSubjectEquivalenceRequest request) {
}
