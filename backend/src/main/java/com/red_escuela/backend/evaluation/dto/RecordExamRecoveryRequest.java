package com.red_escuela.backend.evaluation.dto;

import jakarta.validation.constraints.NotNull;

import com.red_escuela.backend.shared.domain.Grade;

public record RecordExamRecoveryRequest(@NotNull Grade score) {
}
