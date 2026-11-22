package com.red_escuela.backend.graduation.certificate.exam.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ExamCertificateAlreadyIssuedException extends ConflictException implements ReportsErrorCode {

    public ExamCertificateAlreadyIssuedException() {
        super("Ya existe una constancia vigente para esta mesa");
    }
}
