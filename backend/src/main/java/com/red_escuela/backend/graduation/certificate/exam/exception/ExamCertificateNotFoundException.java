package com.red_escuela.backend.graduation.certificate.exam.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ExamCertificateNotFoundException extends NotFoundException implements ReportsErrorCode {

    public ExamCertificateNotFoundException() {
        super("No existe la constancia de examen");
    }
}
