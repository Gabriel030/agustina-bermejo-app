package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CourseResponsibleRequiredException extends BadRequestException {
    public CourseResponsibleRequiredException() {
        super("El curso necesita al menos un preceptor responsable");
    }
}
