package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import com.red_escuela.backend.academicstructure.entity.SubjectStatus;

public class SubjectInvalidTransitionException extends ConflictException {
    public SubjectInvalidTransitionException(Long id, SubjectStatus current, String action) {
        super("No se puede " + action + " la materia " + id + " en estado " + current);
    }
}
