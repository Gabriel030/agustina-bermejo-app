package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateVersionResponse;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateDraftSaver;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SaveCertificateTemplateDraftService implements SaveCertificateTemplateDraftUseCase {

    private final CertificateTemplateDraftSaver saver;

    public SaveCertificateTemplateDraftService(CertificateTemplateDraftSaver saver) {
        this.saver = saver;
    }

    @Override
    public CertificateTemplateVersionResponse save(GraduationCertificateKind kind, CertificateTemplateContent content) {
        return saver.save(kind, content);
    }
}
