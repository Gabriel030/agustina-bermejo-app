package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotNull;

public record CreateSectionEnrollmentRequest(@NotNull Long sectionId) {
}
