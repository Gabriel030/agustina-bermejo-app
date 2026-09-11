package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.GradeItemType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateGradeItemRequest(
        @NotNull GradeItemType type, Integer sequenceNumber, Double weight,
        @NotNull LocalDateTime date, @NotNull Double score, Double maxScore, String comments,
        Long examBoardId) {
}
