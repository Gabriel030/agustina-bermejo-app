package com.red_escuela.backend.attendance.exception;

public class InvalidAttendanceChangeRequestStateException extends RuntimeException {
    public InvalidAttendanceChangeRequestStateException(Long id) {
        super("La solicitud de modificación de asistencia " + id + " ya fue resuelta");
    }
}
