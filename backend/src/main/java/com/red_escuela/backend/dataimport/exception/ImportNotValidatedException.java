package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ImportNotValidatedException extends ConflictException implements ReportsErrorCode {
    public ImportNotValidatedException() {
        super("El lote debe estar validado antes de importar");
    }
}
