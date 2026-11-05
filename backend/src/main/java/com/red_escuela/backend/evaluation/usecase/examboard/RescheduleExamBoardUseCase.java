package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;

public interface RescheduleExamBoardUseCase {
    ExamBoardResponse reschedule(RescheduleExamBoardCommand command);
}
