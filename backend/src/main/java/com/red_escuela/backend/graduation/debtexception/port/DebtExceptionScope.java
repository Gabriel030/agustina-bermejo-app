package com.red_escuela.backend.graduation.debtexception.port;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;

public record DebtExceptionScope(Long exceptionId, Long studentId, CertificateDocumentType documentType,
                                 Long enrollmentId, Long registrationId) {
}
