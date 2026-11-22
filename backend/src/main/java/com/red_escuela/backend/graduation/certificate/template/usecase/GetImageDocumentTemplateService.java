package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageContent;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetImageDocumentTemplateService implements GetImageDocumentTemplateUseCase {

    @Override
    public CertificateTemplateImageContent get(DocumentTemplateType type, Long imageId) {
        throw new DocumentOperationPendingException();
    }
}
