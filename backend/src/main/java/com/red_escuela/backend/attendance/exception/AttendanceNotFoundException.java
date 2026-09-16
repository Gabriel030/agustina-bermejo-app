package com.red_escuela.backend.attendance.exception;

public class AttendanceNotFoundException extends RuntimeException {
    public AttendanceNotFoundException(Long id) {
        super("Asistencia no encontrada: " + id);
    }
}
