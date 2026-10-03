package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class InvalidAttendanceChangeRequestStateException extends ConflictException {
    public InvalidAttendanceChangeRequestStateException(Long id) {
        super("La solicitud de modificación de asistencia " + id + " ya fue resuelta");
    }
}
