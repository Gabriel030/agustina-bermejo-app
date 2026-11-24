package com.red_escuela.backend.graduation.certificate.template.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplate;
import com.red_escuela.backend.graduation.certificate.template.repository.CertificateTemplateRepository;
import org.springframework.stereotype.Component;

@Component
public class CertificateTemplateFinder {

    private final CertificateTemplateRepository repository;

    public CertificateTemplateFinder(CertificateTemplateRepository repository) {
        this.repository = repository;
    }

    public CertificateTemplate find(GraduationCertificateKind kind) {
        return repository.findByKind(kind).orElseThrow(() -> missing(kind));
    }

    public CertificateTemplate findForUpdate(GraduationCertificateKind kind) {
        return repository.findByKindForUpdate(kind).orElseThrow(() -> missing(kind));
    }

    public CertificateTemplate find(DocumentTemplateType type) {
        return repository.findByDocumentType(type).orElseThrow(() -> missing(type));
    }

    public CertificateTemplate findForUpdate(DocumentTemplateType type) {
        return repository.findByDocumentTypeForUpdate(type).orElseThrow(() -> missing(type));
    }

    private IllegalStateException missing(DocumentTemplateType type) {
        return new IllegalStateException("Falta la plantilla de documento " + type);
    }

    private IllegalStateException missing(GraduationCertificateKind kind) {
        return new IllegalStateException("Falta la plantilla de certificado " + kind);
    }
}
