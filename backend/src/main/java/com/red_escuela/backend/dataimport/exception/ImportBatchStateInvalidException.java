package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ImportBatchStateInvalidException extends ConflictException implements ReportsErrorCode {
    public ImportBatchStateInvalidException(String action, String status) {
        super("No se puede " + action + " un lote en estado " + status);
    }
}
