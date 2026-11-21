package com.red_escuela.backend.graduation.certificate.signatory.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class SignatoryNotFoundException extends NotFoundException implements ReportsErrorCode {

    public SignatoryNotFoundException() {
        super("No existe el firmante");
    }
}
