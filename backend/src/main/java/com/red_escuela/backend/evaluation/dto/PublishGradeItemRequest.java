package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.GradeItemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PublishGradeItemRequest(
        @NotNull GradeItemType gradeType,
        @NotNull @Min(1) @Max(1000) Integer sequenceNumber) {
}
