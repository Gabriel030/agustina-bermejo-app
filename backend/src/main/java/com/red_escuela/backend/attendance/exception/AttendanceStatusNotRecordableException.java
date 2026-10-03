package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;

public class AttendanceStatusNotRecordableException extends RuntimeException {
    public AttendanceStatusNotRecordableException(AttendanceStatus status) {
        super("El estado " + status + " solo se alcanza por el circuito de justificación, no se carga directo");
    }
}
