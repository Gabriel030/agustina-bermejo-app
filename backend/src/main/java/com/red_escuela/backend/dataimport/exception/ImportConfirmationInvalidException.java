package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ImportConfirmationInvalidException extends BadRequestException implements ReportsErrorCode {
    public ImportConfirmationInvalidException(String expectedWord) {
        super("Para confirmar hay que escribir " + expectedWord);
    }
}
