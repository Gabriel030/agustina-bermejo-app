package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExamBoardRegistrationAfterExamDateException extends ConflictException {
    public ExamBoardRegistrationAfterExamDateException(Long examBoardId) {
        super("La mesa " + examBoardId + " ya se rindió o su fecha pasó, no admite inscripciones");
    }
}
