package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationEndAfterExamException;
import com.red_escuela.backend.evaluation.exception.InvalidExamBoardRegistrationWindowException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ExamBoardWindowValidator {

    public void require(LocalDateTime examDate, LocalDateTime registrationStart, LocalDateTime registrationEnd) {
        if (registrationStart != null && registrationEnd != null && registrationStart.isAfter(registrationEnd)) {
            throw new InvalidExamBoardRegistrationWindowException();
        }
        if (registrationEnd != null && registrationEnd.isAfter(examDate)) {
            throw new ExamBoardRegistrationEndAfterExamException();
        }
    }
}
