package com.red_escuela.backend.evaluation.usecase.gradecorrection;

import com.red_escuela.backend.evaluation.entity.GradeItemAudit;

public interface CorrectPublishedGradeUseCase {
    GradeItemAudit correct(CorrectPublishedGradeCommand command);
}
