package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SaveDraftDocumentTemplateService implements SaveDraftDocumentTemplateUseCase {

    @Override
    public CertificateTemplateVersionResponse save(DocumentTemplateType type, CertificateTemplateContent content) {
        throw new DocumentOperationPendingException();
    }
}
