package com.red_escuela.backend.graduation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record OwnGraduationRequestResponse(
        Long programEnrollmentId, String programName, boolean graduated, boolean eligible, int accumulatedCredits, int requiredCredits,
        List<String> pendingMandatorySubjects, List<String> unfulfilledElectiveGroups, @Schema(nullable = true) OwnGraduationRequestDetail request) {
}
