package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageUpload;

public interface UploadCertificateTemplateImageUseCase {

    CertificateTemplateImageResponse upload(GraduationCertificateKind kind, CertificateTemplateImageUpload upload);
}
