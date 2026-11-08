package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.dto.ExamBoardResponse;
import com.red_escuela.backend.evaluation.specialist.ExamBoardRescheduler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RescheduleExamBoardService implements RescheduleExamBoardUseCase {

    private final ExamBoardRescheduler rescheduler;

    public RescheduleExamBoardService(ExamBoardRescheduler rescheduler) {
        this.rescheduler = rescheduler;
    }

    @Override
    public ExamBoardResponse reschedule(RescheduleExamBoardCommand command) {
        return rescheduler.reschedule(command.examBoardId(), command.request());
    }
}
