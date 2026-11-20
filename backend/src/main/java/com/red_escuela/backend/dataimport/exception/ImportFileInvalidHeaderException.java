package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ImportFileInvalidHeaderException extends BadRequestException implements ReportsErrorCode {
    public ImportFileInvalidHeaderException(String detail) {
        super("Cabecera inválida: " + detail);
    }
}
