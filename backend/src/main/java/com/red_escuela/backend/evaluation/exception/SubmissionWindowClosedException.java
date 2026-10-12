package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class SubmissionWindowClosedException extends BadRequestException {
    public SubmissionWindowClosedException(Long sectionId) {
        super("Venció el plazo para enviar la configuración de evaluación de la comisión " + sectionId);
    }
}
