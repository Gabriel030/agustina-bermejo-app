package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewCertificateTemplateService implements PreviewCertificateTemplateUseCase {

    @Override
    public GraduationCertificatePdf preview(GraduationCertificateKind kind, TemplatePreviewSource source) {
        throw new GraduationCertificateOperationPendingException();
    }
}
