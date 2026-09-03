package com.red_escuela.backend.exception;

public class AttendanceChangeRequestNotFoundException extends RuntimeException {
    public AttendanceChangeRequestNotFoundException(Long id) {
        super("Solicitud de modificación de asistencia no encontrada: " + id);
    }
}
