package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class AttendanceAlreadyRecordedException extends ConflictException {
    public AttendanceAlreadyRecordedException(Long enrollmentId, String classDate) {
        super("Ya existe asistencia cargada para la inscripción " + enrollmentId + " en la fecha " + classDate);
    }
}
