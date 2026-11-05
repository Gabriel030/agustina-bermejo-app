package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.entity.ExamBoard;

public interface CancelExamBoardUseCase {
    ExamBoard cancel(CancelExamBoardCommand command);
}
