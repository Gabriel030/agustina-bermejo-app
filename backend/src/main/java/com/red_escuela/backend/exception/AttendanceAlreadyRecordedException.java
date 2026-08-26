package com.red_escuela.backend.exception;

// Violación defensiva del índice único parcial de attendance (alumno+comisión+fecha); no
// debería pasar si el service reactiva la fila existente en vez de insertar una nueva.
public class AttendanceAlreadyRecordedException extends RuntimeException {
    public AttendanceAlreadyRecordedException(Long enrollmentId, String classDate) {
        super("Ya existe asistencia cargada para la inscripción " + enrollmentId + " en la fecha " + classDate);
    }
}
