package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotNull;

public record CreateProgramEnrollmentRequest(@NotNull Long programId) {
}
