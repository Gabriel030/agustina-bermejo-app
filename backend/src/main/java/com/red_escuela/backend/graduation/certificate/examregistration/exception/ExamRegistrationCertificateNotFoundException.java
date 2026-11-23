package com.red_escuela.backend.graduation.certificate.examregistration.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ExamRegistrationCertificateNotFoundException extends NotFoundException implements ReportsErrorCode {

    public ExamRegistrationCertificateNotFoundException() {
        super("No encontramos el documento");
    }
}
