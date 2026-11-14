package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class EnrollmentChangeReasonRequiredException extends BadRequestException {
    public EnrollmentChangeReasonRequiredException() {
        super("Indicá el motivo del cambio: es obligatorio y queda registrado");
    }
}
