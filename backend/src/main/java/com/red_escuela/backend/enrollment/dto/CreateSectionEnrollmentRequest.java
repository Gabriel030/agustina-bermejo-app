package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateSectionEnrollmentRequest(@NotNull Long sectionId) {
}
