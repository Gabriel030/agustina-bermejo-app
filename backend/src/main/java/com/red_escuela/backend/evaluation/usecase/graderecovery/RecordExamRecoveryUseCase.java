package com.red_escuela.backend.evaluation.usecase.graderecovery;

import com.red_escuela.backend.evaluation.dto.GradeItemAuditResponse;

public interface RecordExamRecoveryUseCase {
    GradeItemAuditResponse record(RecordExamRecoveryCommand command);
}
