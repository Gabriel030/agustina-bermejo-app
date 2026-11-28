package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class SubmissionWindowClosedException extends ConflictException {
    public SubmissionWindowClosedException(Long sectionId) {
        super("Venció el plazo para enviar la configuración de evaluación de la comisión " + sectionId);
    }
}
