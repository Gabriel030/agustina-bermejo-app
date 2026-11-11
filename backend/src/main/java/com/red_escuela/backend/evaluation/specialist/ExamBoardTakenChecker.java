package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotYetTakenException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class ExamBoardTakenChecker {

    private final Clock clock;

    public ExamBoardTakenChecker(Clock clock) {
        this.clock = clock;
    }

    public void requireTaken(ExamBoard examBoard) {
        if (LocalDateTime.now(clock).isBefore(examBoard.getExamDate())) {
            throw new ExamBoardNotYetTakenException(examBoard.getId(), examBoard.getExamDate());
        }
    }
}
