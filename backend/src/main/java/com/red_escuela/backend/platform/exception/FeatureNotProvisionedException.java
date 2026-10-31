package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.exception.DataIntegrityBrokenException;

public class FeatureNotProvisionedException extends DataIntegrityBrokenException {
    public FeatureNotProvisionedException(FeatureCode code) {
        super("Falta la fila de platform_feature para " + code);
    }
}
