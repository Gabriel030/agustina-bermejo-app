package com.red_escuela.backend.graduation.certificate.exam.exception;

import com.red_escuela.backend.shared.exception.UnprocessableEntityException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ExamCertificateBoardNotHeldException extends UnprocessableEntityException implements ReportsErrorCode {

    public ExamCertificateBoardNotHeldException() {
        super("La mesa todavía no se rindió");
    }
}
