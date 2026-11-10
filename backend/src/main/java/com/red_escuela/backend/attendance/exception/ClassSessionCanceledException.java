package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import java.time.LocalDate;

public class ClassSessionCanceledException extends ConflictException {
    public ClassSessionCanceledException(LocalDate classDate) {
        super("No se puede tomar asistencia el " + classDate + ": la clase está cancelada");
    }
}
