package com.red_escuela.backend.graduation.certificate.engine;

public record CertificateTemplateDefinition(
        String templateRef,
        PageFormat pageFormat,
        String headline,
        String bodyTemplate,
        Rect bodyRect,
        Rect qrRect,
        Rect codeRect,
        Rect signatoryRect) {

    public CertificateTemplateDefinition {
        bodyTemplate = bodyTemplate == null ? "" : bodyTemplate;
    }
}
