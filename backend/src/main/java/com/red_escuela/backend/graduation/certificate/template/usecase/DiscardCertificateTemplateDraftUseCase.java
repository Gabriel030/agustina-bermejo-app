package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;

public interface DiscardCertificateTemplateDraftUseCase {

    void discard(GraduationCertificateKind kind);
}
