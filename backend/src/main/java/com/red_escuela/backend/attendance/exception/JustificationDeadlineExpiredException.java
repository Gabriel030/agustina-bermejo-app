package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class JustificationDeadlineExpiredException extends ConflictException {
    public JustificationDeadlineExpiredException(Long attendanceId) {
        super("Venció el plazo para justificar la asistencia " + attendanceId);
    }
}
