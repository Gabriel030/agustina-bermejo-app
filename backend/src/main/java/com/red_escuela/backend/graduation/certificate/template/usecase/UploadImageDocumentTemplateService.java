package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageUpload;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UploadImageDocumentTemplateService implements UploadImageDocumentTemplateUseCase {

    @Override
    public CertificateTemplateImageResponse upload(DocumentTemplateType type, CertificateTemplateImageUpload upload) {
        throw new DocumentOperationPendingException();
    }
}
