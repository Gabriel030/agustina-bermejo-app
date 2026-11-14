package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ImportBatchNotFoundException extends NotFoundException implements ReportsErrorCode {
    public ImportBatchNotFoundException(Long batchId) {
        super("Lote de importación no encontrado: " + batchId);
    }
}
