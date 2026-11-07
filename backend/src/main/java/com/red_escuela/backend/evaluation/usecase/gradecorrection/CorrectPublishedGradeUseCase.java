package com.red_escuela.backend.evaluation.usecase.gradecorrection;

import com.red_escuela.backend.evaluation.dto.GradeItemAuditResponse;

public interface CorrectPublishedGradeUseCase {
    GradeItemAuditResponse correct(CorrectPublishedGradeCommand command);
}
