package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;

public interface PreviewCertificateTemplateUseCase {

    GraduationCertificatePdf preview(GraduationCertificateKind kind, TemplatePreviewSource source);
}
