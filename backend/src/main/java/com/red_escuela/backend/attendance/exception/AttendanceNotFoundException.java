package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class AttendanceNotFoundException extends NotFoundException {
    public AttendanceNotFoundException(Long id) {
        super("Asistencia no encontrada: " + id);
    }
}
