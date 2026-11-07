package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.shared.domain.Grade;
import jakarta.validation.constraints.NotNull;

public record CorrectGradeItemRequest(@NotNull Grade newScore, @NotNull String reason) {
}
