package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionSummaryResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import java.util.List;

public interface ListVersionsDocumentTemplateUseCase {

    List<CertificateTemplateVersionSummaryResponse> list(DocumentTemplateType type);
}
