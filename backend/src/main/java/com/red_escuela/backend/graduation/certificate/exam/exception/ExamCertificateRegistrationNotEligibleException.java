package com.red_escuela.backend.graduation.certificate.exam.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ExamCertificateRegistrationNotEligibleException extends UnprocessableEntityException implements ReportsErrorCode {

    public ExamCertificateRegistrationNotEligibleException() {
        super("La inscripción no corresponde a un alumno presente en la mesa");
    }
}
