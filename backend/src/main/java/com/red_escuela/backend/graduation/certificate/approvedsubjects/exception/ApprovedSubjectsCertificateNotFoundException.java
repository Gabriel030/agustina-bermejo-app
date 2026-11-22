package com.red_escuela.backend.graduation.certificate.approvedsubjects.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class ApprovedSubjectsCertificateNotFoundException extends NotFoundException implements ReportsErrorCode {

    public ApprovedSubjectsCertificateNotFoundException() {
        super("No existe la constancia de materias aprobadas");
    }
}
