package com.red_escuela.backend.graduation.certificate.engine;

import java.util.List;

public record CertificateAssets(byte[] logo, List<byte[]> signatureImages, String registrationNumber, CertificateTexts texts) {

    public static final CertificateAssets NONE = new CertificateAssets(null, List.of(), null, CertificateTexts.DEFAULT);

    public CertificateAssets {
        signatureImages = signatureImages == null ? List.of() : signatureImages;
        texts = texts == null ? CertificateTexts.DEFAULT : texts;
    }
}
