package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageUpload;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateImageUploader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UploadImageDocumentTemplateService implements UploadImageDocumentTemplateUseCase {

    private final CertificateTemplateImageUploader delegate;

    public UploadImageDocumentTemplateService(CertificateTemplateImageUploader delegate) {
        this.delegate = delegate;
    }

    @Override
    public CertificateTemplateImageResponse upload(DocumentTemplateType type, CertificateTemplateImageUpload upload) {
        return delegate.upload(type, upload);
    }
}
