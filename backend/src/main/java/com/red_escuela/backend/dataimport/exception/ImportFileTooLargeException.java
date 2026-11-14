package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ImportFileTooLargeException extends BadRequestException implements ReportsErrorCode {
    public ImportFileTooLargeException(long maxBytes) {
        super("El archivo supera el máximo de " + maxBytes + " bytes");
    }
}
