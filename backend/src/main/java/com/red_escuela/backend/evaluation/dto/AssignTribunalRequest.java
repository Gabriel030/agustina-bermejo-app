package com.red_escuela.backend.evaluation.dto;

import jakarta.validation.constraints.NotNull;

public record AssignTribunalRequest(@NotNull Long instructorId) {
}
