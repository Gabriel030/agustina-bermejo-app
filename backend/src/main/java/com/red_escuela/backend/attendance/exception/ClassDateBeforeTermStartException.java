package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDate;

public class ClassDateBeforeTermStartException extends BadRequestException {
    public ClassDateBeforeTermStartException(LocalDate date) {
        super("No se puede tomar asistencia el " + date + ": es anterior al inicio del período");
    }
}
