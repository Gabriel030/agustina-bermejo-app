package com.red_escuela.backend.evaluation.dto;

import java.math.BigDecimal;

import com.red_escuela.backend.evaluation.entity.GradeItemType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateGradeItemRequest(
        @NotNull GradeItemType type, Integer sequenceNumber, Double weight,
        @NotNull LocalDateTime date, @NotNull BigDecimal score, String comments,
        Long examBoardId) {
}
