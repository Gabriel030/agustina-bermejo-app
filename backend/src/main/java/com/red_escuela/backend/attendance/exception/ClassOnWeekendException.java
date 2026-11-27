package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDate;

public class ClassOnWeekendException extends BadRequestException {
    public ClassOnWeekendException(LocalDate date) {
        super("No se puede tomar asistencia el " + date + ": es fin de semana y la comisión no tiene clase ese día");
    }
}
