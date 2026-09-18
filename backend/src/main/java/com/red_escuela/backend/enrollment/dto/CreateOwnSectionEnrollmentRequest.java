package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateOwnSectionEnrollmentRequest(@NotNull Long programEnrollmentId, @NotNull Long sectionId) {
}
