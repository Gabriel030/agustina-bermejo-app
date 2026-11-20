package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCertificateTemplateService implements GetCertificateTemplateUseCase {

    @Override
    public CertificateTemplateResponse get(GraduationCertificateKind kind) {
        throw new GraduationCertificateOperationPendingException();
    }
}
