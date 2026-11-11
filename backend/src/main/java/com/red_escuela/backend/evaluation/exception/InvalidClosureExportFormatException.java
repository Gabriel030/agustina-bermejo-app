package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class InvalidClosureExportFormatException extends BadRequestException {
    public InvalidClosureExportFormatException(String format) {
        super("El formato de exportación '" + format + "' no es válido: se admite xlsx o pdf");
    }
}
