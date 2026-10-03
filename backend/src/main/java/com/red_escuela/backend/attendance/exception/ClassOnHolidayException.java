package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDate;

public class ClassOnHolidayException extends BadRequestException {
    public ClassOnHolidayException(LocalDate date) {
        super("No se puede tomar asistencia el " + date + ": es feriado");
    }
}
