package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.entity.ExceptionType;

public class DirectorApprovalSubjectRequiredException extends RuntimeException {
    public DirectorApprovalSubjectRequiredException(ExceptionType type) {
        super("Una excepción de tipo " + type + " tiene que indicar la materia");
    }
}
