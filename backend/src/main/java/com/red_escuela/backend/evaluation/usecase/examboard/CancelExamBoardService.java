package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.specialist.ExamBoardCanceler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CancelExamBoardService implements CancelExamBoardUseCase {

    private final ExamBoardCanceler canceler;

    public CancelExamBoardService(ExamBoardCanceler canceler) {
        this.canceler = canceler;
    }

    @Override
    public ExamBoardResponse cancel(CancelExamBoardCommand command) {
        return canceler.cancel(command.examBoardId(), command.reason());
    }
}
