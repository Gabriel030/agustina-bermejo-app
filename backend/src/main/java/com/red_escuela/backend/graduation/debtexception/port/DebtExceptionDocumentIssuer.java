package com.red_escuela.backend.graduation.debtexception.port;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;

public interface DebtExceptionDocumentIssuer {

    boolean supports(CertificateDocumentType type);

    void requireScopeBelongsToStudent(DebtExceptionScope scope);

    DebtExceptionDocument render(DebtExceptionScope scope);
}
