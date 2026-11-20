package com.red_escuela.backend.graduation.certificate.engine;

import java.util.List;

public record CertificateAssets(byte[] logo, List<byte[]> signatureImages, String registrationNumber) {

    public static final CertificateAssets NONE = new CertificateAssets(null, List.of(), null);

    public CertificateAssets {
        signatureImages = signatureImages == null ? List.of() : signatureImages;
    }
}
