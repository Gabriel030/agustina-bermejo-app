package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;

public interface GetCertificateTemplateVersionUseCase {

    CertificateTemplateVersionResponse get(GraduationCertificateKind kind, int versionNumber);
}
