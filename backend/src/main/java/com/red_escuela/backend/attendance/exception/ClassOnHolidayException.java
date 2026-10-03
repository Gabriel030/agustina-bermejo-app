package com.red_escuela.backend.attendance.exception;

import java.time.LocalDate;

public class ClassOnHolidayException extends RuntimeException {
    public ClassOnHolidayException(LocalDate date) {
        super("No se puede tomar asistencia el " + date + ": es feriado");
    }
}
