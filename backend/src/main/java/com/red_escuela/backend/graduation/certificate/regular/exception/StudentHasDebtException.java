package com.red_escuela.backend.graduation.certificate.regular.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class StudentHasDebtException extends ConflictException {

    public StudentHasDebtException() {
        super("El alumno tiene deuda vencida o matrícula impaga");
    }
}
