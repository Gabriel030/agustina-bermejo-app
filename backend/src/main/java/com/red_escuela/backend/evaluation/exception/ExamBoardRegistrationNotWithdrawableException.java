package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExamBoardRegistrationNotWithdrawableException extends ConflictException {
    public ExamBoardRegistrationNotWithdrawableException(String message) {
        super(message);
    }
}
