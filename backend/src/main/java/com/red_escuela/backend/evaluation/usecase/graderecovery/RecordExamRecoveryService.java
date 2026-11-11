package com.red_escuela.backend.evaluation.usecase.graderecovery;

import com.red_escuela.backend.evaluation.dto.GradeItemAuditResponse;
import com.red_escuela.backend.evaluation.specialist.ExamRecoveryRecorder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RecordExamRecoveryService implements RecordExamRecoveryUseCase {

    private final ExamRecoveryRecorder recorder;

    public RecordExamRecoveryService(ExamRecoveryRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public GradeItemAuditResponse record(RecordExamRecoveryCommand command) {
        return recorder.record(command.gradeItemId(), command.instructorId(), command.actorPersonId(), command.request());
    }
}
