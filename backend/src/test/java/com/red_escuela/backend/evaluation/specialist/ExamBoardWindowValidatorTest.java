package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationEndAfterExamException;
import com.red_escuela.backend.evaluation.exception.InvalidExamBoardRegistrationWindowException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamBoardWindowValidatorTest {

    private static final LocalDateTime EXAM = LocalDateTime.of(2026, 12, 10, 18, 0);

    private final ExamBoardWindowValidator validator = new ExamBoardWindowValidator();

    @Test
    void startAfterEndIsRejected() {
        assertThatThrownBy(() -> validator.require(EXAM, EXAM.minusDays(1), EXAM.minusDays(5)))
                .isInstanceOf(InvalidExamBoardRegistrationWindowException.class);
    }

    @Test
    void endAfterTheExamIsRejected() {
        assertThatThrownBy(() -> validator.require(EXAM, EXAM.minusDays(5), EXAM.plusMinutes(1)))
                .isInstanceOf(ExamBoardRegistrationEndAfterExamException.class);
    }

    @Test
    void anOrderedWindowClosingAtTheExamIsAccepted() {
        assertThatCode(() -> validator.require(EXAM, EXAM.minusDays(5), EXAM)).doesNotThrowAnyException();
    }

    @Test
    void anOpenEndedWindowIsAccepted() {
        assertThatCode(() -> validator.require(EXAM, EXAM.minusDays(5), null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.require(EXAM, null, null)).doesNotThrowAnyException();
    }
}
