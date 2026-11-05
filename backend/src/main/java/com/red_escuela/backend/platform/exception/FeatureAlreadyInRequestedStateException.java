package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.exception.ConflictException;

public class FeatureAlreadyInRequestedStateException extends ConflictException {
    public FeatureAlreadyInRequestedStateException(FeatureCode code) {
        super(code + " ya está en el estado pedido");
    }
}
