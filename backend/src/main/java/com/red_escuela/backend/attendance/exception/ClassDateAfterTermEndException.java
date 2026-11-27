package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDate;

public class ClassDateAfterTermEndException extends BadRequestException {
    public ClassDateAfterTermEndException(LocalDate date) {
        super("No se puede tomar asistencia el " + date + ": es posterior al fin del período");
    }
}
