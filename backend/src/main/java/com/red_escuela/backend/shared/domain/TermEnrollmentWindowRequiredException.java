package com.red_escuela.backend.shared.domain;

import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class TermEnrollmentWindowRequiredException extends BadRequestException implements ReportsErrorCode {

    public TermEnrollmentWindowRequiredException() {
        super("El período necesita una ventana de inscripción con fecha de inicio y de fin");
    }
}
