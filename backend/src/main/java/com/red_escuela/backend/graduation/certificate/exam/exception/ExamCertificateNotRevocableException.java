package com.red_escuela.backend.graduation.certificate.exam.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ExamCertificateNotRevocableException extends ConflictException implements ReportsErrorCode {

    public ExamCertificateNotRevocableException() {
        super("La constancia de examen ya no se puede revocar");
    }
}
