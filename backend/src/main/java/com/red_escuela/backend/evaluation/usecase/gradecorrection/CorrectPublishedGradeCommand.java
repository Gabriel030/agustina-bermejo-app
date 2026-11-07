package com.red_escuela.backend.evaluation.usecase.gradecorrection;

import com.red_escuela.backend.evaluation.dto.CorrectGradeItemRequest;

public record CorrectPublishedGradeCommand(Long gradeItemId, Long actorPersonId, CorrectGradeItemRequest request) {
}
