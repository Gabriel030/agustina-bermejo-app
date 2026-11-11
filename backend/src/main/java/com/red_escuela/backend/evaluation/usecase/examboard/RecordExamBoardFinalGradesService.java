package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradesResponse;
import com.red_escuela.backend.evaluation.specialist.ExamBoardFinalGradesRecorder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RecordExamBoardFinalGradesService implements RecordExamBoardFinalGradesUseCase {

    private final ExamBoardFinalGradesRecorder recorder;

    public RecordExamBoardFinalGradesService(ExamBoardFinalGradesRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public ExamBoardFinalGradesResponse record(RecordExamBoardFinalGradesCommand command) {
        return recorder.record(command.examBoardId(), command.instructorId(), command.entries());
    }
}
