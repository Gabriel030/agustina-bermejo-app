package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.exception.BadRequestException;

public class FeatureValueInvalidException extends BadRequestException {
    public FeatureValueInvalidException(FeatureCode code, String detail) {
        super("Valor inválido para " + code + ": " + detail);
    }
}
