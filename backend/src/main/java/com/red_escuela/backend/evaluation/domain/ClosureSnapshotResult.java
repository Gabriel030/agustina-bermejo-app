package com.red_escuela.backend.evaluation.domain;

import java.util.List;

public record ClosureSnapshotResult(List<ClosureStudentAssessment> assessments, List<Long> pendingEnrollmentIds) {
}
