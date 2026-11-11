package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradesResponse;

public interface RecordExamBoardFinalGradesUseCase {
    ExamBoardFinalGradesResponse record(RecordExamBoardFinalGradesCommand command);
}
