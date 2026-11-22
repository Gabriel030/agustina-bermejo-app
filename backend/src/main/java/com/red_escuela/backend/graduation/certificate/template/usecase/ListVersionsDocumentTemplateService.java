package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionSummaryResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class ListVersionsDocumentTemplateService implements ListVersionsDocumentTemplateUseCase {

    @Override
    public List<CertificateTemplateVersionSummaryResponse> list(DocumentTemplateType type) {
        throw new DocumentOperationPendingException();
    }
}
