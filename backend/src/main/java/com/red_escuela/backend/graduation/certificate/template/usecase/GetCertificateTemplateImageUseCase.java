package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateImageContent;

public interface GetCertificateTemplateImageUseCase {

    CertificateTemplateImageContent get(GraduationCertificateKind kind, Long imageId);
}
