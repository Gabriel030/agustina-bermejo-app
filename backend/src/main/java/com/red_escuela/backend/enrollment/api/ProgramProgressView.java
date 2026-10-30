package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.domain.Grade;

import java.util.List;

public record ProgramProgressView(
        Long programEnrollmentId, int accumulatedCredits, int requiredCredits,
        List<String> pendingMandatorySubjects, List<ElectiveGroupProgressView> electiveGroups,
        boolean eligibleToComplete, boolean regular, Grade overallAverage) {
}
