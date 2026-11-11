package com.red_escuela.backend.evaluation.usecase.graderecovery;

import com.red_escuela.backend.evaluation.dto.RecordExamRecoveryRequest;

public record RecordExamRecoveryCommand(Long gradeItemId, Long instructorId, Long actorPersonId,
                                        RecordExamRecoveryRequest request) {
}
