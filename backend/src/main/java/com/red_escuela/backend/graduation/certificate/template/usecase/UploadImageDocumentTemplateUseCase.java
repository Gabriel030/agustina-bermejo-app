package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageUpload;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;

public interface UploadImageDocumentTemplateUseCase {

    CertificateTemplateImageResponse upload(DocumentTemplateType type, CertificateTemplateImageUpload upload);
}
