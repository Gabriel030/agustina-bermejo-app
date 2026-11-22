package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;

public interface PreviewDocumentTemplateUseCase {

    GraduationCertificatePdf preview(DocumentTemplateType type, TemplatePreviewSource source);
}
