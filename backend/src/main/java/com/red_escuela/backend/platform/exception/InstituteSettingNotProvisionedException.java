package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.shared.exception.DataIntegrityBrokenException;

public class InstituteSettingNotProvisionedException extends DataIntegrityBrokenException {
    public InstituteSettingNotProvisionedException(InstituteSettingCode code) {
        super("Falta la fila de institute_setting para " + code);
    }
}
