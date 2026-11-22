package com.red_escuela.backend.graduation.certificate.approvedsubjects.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ApprovedSubjectsCertificateNotAvailableException extends ConflictException implements ReportsErrorCode {

    public ApprovedSubjectsCertificateNotAvailableException() {
        super("La constancia de materias aprobadas no está vigente");
    }
}
