package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ClassSessionAlreadyCanceledException extends ConflictException {
    public ClassSessionAlreadyCanceledException(Long classSessionId) {
        super("La clase " + classSessionId + " ya está cancelada");
    }
}
