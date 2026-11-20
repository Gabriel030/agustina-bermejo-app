package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DiscardCertificateTemplateDraftService implements DiscardCertificateTemplateDraftUseCase {

    @Override
    public void discard(GraduationCertificateKind kind) {
        throw new GraduationCertificateOperationPendingException();
    }
}
