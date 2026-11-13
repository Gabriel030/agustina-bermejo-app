package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class RegularEnrollmentNotFoundException extends NotFoundException {

    public RegularEnrollmentNotFoundException() {
        super("La inscripción no existe");
    }
}
