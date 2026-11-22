package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class SectionHasRecordsException extends ConflictException implements ReportsErrorCode {

    public SectionHasRecordsException() {
        super("Para cambiar de comisión hablá con Administración");
    }
}
