package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplatePreviewer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewDocumentTemplateService implements PreviewDocumentTemplateUseCase {

    private final CertificateTemplatePreviewer previewer;

    public PreviewDocumentTemplateService(CertificateTemplatePreviewer previewer) {
        this.previewer = previewer;
    }

    @Override
    public GraduationCertificatePdf preview(DocumentTemplateType type, TemplatePreviewSource source) {
        return previewer.preview(type, source);
    }
}
