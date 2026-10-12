package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import com.red_escuela.backend.enrollment.entity.ExceptionType;

public class DirectorApprovalSubjectRequiredException extends BadRequestException {
    public DirectorApprovalSubjectRequiredException(ExceptionType type) {
        super("Una excepción de tipo " + type + " tiene que indicar la materia");
    }
}
