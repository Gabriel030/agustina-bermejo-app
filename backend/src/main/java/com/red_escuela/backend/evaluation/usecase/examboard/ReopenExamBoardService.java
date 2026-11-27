package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.specialist.ExamBoardReopener;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReopenExamBoardService implements ReopenExamBoardUseCase {

    private final ExamBoardReopener reopener;

    public ReopenExamBoardService(ExamBoardReopener reopener) {
        this.reopener = reopener;
    }

    @Override
    public ExamBoardResponse reopen(ReopenExamBoardCommand command) {
        return reopener.reopen(command.examBoardId(), command.reason());
    }
}
