package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.exception.ExamBoardDateInPastException;
import com.red_escuela.backend.evaluation.exception.ExamBoardDateOnWeekendException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Component
public class ExamBoardRescheduleDateValidator {

    private static final Set<DayOfWeek> WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private final Clock clock;

    public ExamBoardRescheduleDateValidator(Clock clock) {
        this.clock = clock;
    }

    public void require(LocalDateTime examDate) {
        if (examDate.isBefore(LocalDateTime.now(clock))) {
            throw new ExamBoardDateInPastException(examDate);
        }
        if (WEEKEND.contains(examDate.getDayOfWeek())) {
            throw new ExamBoardDateOnWeekendException(examDate.toLocalDate());
        }
    }
}
