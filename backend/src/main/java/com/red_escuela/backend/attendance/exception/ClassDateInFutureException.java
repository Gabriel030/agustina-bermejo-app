package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDate;

public class ClassDateInFutureException extends BadRequestException {
    public ClassDateInFutureException(LocalDate date) {
        super("No se puede tomar asistencia el " + date + ": es una fecha futura");
    }
}
