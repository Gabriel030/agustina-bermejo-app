package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ImportTooManyRowsException extends BadRequestException implements ReportsErrorCode {
    public ImportTooManyRowsException(int maxRows) {
        super("El archivo supera el máximo de " + maxRows + " filas");
    }
}
