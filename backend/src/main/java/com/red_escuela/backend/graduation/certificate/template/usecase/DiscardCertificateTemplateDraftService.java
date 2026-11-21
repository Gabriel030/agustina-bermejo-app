package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateDraftDiscarder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DiscardCertificateTemplateDraftService implements DiscardCertificateTemplateDraftUseCase {

    private final CertificateTemplateDraftDiscarder discarder;

    public DiscardCertificateTemplateDraftService(CertificateTemplateDraftDiscarder discarder) {
        this.discarder = discarder;
    }

    @Override
    public void discard(GraduationCertificateKind kind) {
        discarder.discard(kind);
    }
}
