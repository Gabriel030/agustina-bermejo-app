package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplatePreviewer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewCertificateTemplateService implements PreviewCertificateTemplateUseCase {

    private final CertificateTemplatePreviewer previewer;

    public PreviewCertificateTemplateService(CertificateTemplatePreviewer previewer) {
        this.previewer = previewer;
    }

    @Override
    public GraduationCertificatePdf preview(GraduationCertificateKind kind, TemplatePreviewSource source) {
        return previewer.preview(kind, source);
    }
}
