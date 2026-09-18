package com.red_escuela.backend.evaluation.dto;

import jakarta.validation.constraints.NotNull;

public record CreateExamBoardRegistrationRequest(
        @NotNull Long examBoardId, Long programSectionEnrollmentId, Long courseSectionEnrollmentId) {
}
