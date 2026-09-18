package com.red_escuela.backend.exception;

public class AttendanceAlreadyRecordedException extends RuntimeException {
    public AttendanceAlreadyRecordedException(Long enrollmentId, String classDate) {
        super("Ya existe asistencia cargada para la inscripción " + enrollmentId + " en la fecha " + classDate);
    }
}
