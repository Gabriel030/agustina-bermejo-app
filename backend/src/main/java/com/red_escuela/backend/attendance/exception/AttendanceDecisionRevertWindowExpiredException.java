package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AttendanceDecisionRevertWindowExpiredException extends ConflictException {
    public AttendanceDecisionRevertWindowExpiredException(Long id) {
        super("Venció el plazo para revertir la decisión de la solicitud " + id);
    }
}
