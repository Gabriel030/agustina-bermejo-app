package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;

public interface ReopenExamBoardUseCase {
    ExamBoardResponse reopen(ReopenExamBoardCommand command);
}
