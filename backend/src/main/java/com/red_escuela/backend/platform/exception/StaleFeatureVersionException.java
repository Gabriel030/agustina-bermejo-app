package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.exception.ConflictException;

public class StaleFeatureVersionException extends ConflictException {
    public StaleFeatureVersionException(FeatureCode code, long expected, long actual) {
        super(code + " cambió mientras lo editabas (versión pedida " + expected + ", actual " + actual + ")");
    }
}
