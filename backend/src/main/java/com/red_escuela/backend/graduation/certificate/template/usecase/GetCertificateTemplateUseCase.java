package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateResponse;

public interface GetCertificateTemplateUseCase {

    CertificateTemplateResponse get(GraduationCertificateKind kind);
}
