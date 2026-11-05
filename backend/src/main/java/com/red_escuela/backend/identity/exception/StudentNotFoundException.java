package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class StudentNotFoundException extends NotFoundException implements ReportsErrorCode {
    public StudentNotFoundException(Long id) {
        super("Student no encontrado: " + id);
    }
}
