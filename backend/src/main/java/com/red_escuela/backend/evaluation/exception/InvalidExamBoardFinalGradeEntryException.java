package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidExamBoardFinalGradeEntryException extends BadRequestException {
    public InvalidExamBoardFinalGradeEntryException(String message) {
        super(message);
    }
}
