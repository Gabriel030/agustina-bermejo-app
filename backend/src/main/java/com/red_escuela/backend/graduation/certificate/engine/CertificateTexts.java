package com.red_escuela.backend.graduation.certificate.engine;

public record CertificateTexts(
        String instituteName,
        String title,
        String intro,
        String approval,
        String dictation,
        String qrLabel,
        String legend) {

    public static final CertificateTexts DEFAULT = new CertificateTexts(null, null, null, null, null, null, null);
}
