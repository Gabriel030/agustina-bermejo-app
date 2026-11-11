package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ExamBoardRegistrationWithdrawnException extends ConflictException {
    public ExamBoardRegistrationWithdrawnException(Long registrationId) {
        super("La inscripción " + registrationId + " fue dada de baja: no admite notas");
    }
}
