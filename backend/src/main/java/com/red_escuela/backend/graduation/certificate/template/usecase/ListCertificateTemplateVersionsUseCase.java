package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionSummaryResponse;
import java.util.List;

public interface ListCertificateTemplateVersionsUseCase {

    List<CertificateTemplateVersionSummaryResponse> list(GraduationCertificateKind kind);
}
