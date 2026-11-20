package com.red_escuela.backend.graduation.certificate.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class BatchSizeInvalidException extends BadRequestException {

    public BatchSizeInvalidException() {
        super("El lote debe tener entre 1 y 50 certificados");
    }
}
