package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetVersionDocumentTemplateService implements GetVersionDocumentTemplateUseCase {

    @Override
    public CertificateTemplateVersionResponse get(DocumentTemplateType type, int number) {
        throw new DocumentOperationPendingException();
    }
}
