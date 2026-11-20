package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateTemplateVersionService implements GetCertificateTemplateVersionUseCase {

    @Override
    public CertificateTemplateVersionResponse get(GraduationCertificateKind kind, int versionNumber) {
        throw new GraduationCertificateOperationPendingException();
    }
}
