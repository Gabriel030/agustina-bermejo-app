package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionSummaryResponse;
import java.util.List;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListCertificateTemplateVersionsService implements ListCertificateTemplateVersionsUseCase {

    @Override
    public List<CertificateTemplateVersionSummaryResponse> list(GraduationCertificateKind kind) {
        throw new GraduationCertificateOperationPendingException();
    }
}
