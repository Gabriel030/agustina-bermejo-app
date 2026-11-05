package com.red_escuela.backend.graduation.certificate.engine;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;

public interface CertificateTemplateResolver {

    CertificateTemplateDefinition resolve(GraduationCertificateKind kind, Long titleId);
}
