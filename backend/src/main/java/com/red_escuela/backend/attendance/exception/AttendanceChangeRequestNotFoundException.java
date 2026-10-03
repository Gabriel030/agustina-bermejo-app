package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class AttendanceChangeRequestNotFoundException extends NotFoundException {
    public AttendanceChangeRequestNotFoundException(Long id) {
        super("Solicitud de modificación de asistencia no encontrada: " + id);
    }
}
