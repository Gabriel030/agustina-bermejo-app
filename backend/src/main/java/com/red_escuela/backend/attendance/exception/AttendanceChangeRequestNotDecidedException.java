package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AttendanceChangeRequestNotDecidedException extends ConflictException {
    public AttendanceChangeRequestNotDecidedException(Long id) {
        super("La solicitud de modificación de asistencia " + id + " todavía no fue resuelta");
    }
}
