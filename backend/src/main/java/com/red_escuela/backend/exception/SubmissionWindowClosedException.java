package com.red_escuela.backend.exception;

public class SubmissionWindowClosedException extends RuntimeException {
    public SubmissionWindowClosedException(Long sectionId) {
        super("Venció el plazo para enviar la configuración de evaluación de la comisión " + sectionId);
    }
}
