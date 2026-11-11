package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.GradeItemType;

public record ClosureSheetColumnResponse(GradeItemType type, int sequenceNumber, boolean published) {
}
