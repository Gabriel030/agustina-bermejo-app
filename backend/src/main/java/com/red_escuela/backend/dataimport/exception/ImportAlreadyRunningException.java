package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ImportAlreadyRunningException extends ConflictException implements ReportsErrorCode {
    public ImportAlreadyRunningException() {
        super("Ya hay una importación en curso");
    }
}
