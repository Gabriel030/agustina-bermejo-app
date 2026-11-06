package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.shared.exception.ConflictException;

public class InstituteSettingAlreadyInRequestedStateException extends ConflictException {
    public InstituteSettingAlreadyInRequestedStateException(InstituteSettingCode code) {
        super(code + " ya está en el estado pedido");
    }
}
