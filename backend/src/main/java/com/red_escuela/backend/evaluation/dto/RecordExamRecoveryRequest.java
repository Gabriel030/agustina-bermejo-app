package com.red_escuela.backend.evaluation.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordExamRecoveryRequest(@NotNull BigDecimal score) {
}
