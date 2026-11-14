package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ImportFileUnreadableException extends BadRequestException implements ReportsErrorCode {
    public ImportFileUnreadableException() {
        super("No se pudo leer el archivo");
    }
}
