package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewDocumentTemplateService implements PreviewDocumentTemplateUseCase {

    @Override
    public GraduationCertificatePdf preview(DocumentTemplateType type, TemplatePreviewSource source) {
        throw new DocumentOperationPendingException();
    }
}
