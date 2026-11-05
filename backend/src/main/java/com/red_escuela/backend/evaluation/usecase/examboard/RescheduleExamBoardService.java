package com.red_escuela.backend.evaluation.usecase.examboard;

import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.service.ExamBoardRescheduler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RescheduleExamBoardService implements RescheduleExamBoardUseCase {

    private final ExamBoardRescheduler rescheduler;

    public RescheduleExamBoardService(ExamBoardRescheduler rescheduler) {
        this.rescheduler = rescheduler;
    }

    @Override
    public ExamBoard reschedule(RescheduleExamBoardCommand command) {
        return rescheduler.reschedule(command.examBoardId(), command.request());
    }
}
