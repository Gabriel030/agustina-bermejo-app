package com.red_escuela.backend.graduation.certificate.regular.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegularCertificateSubmissionResponse(@Schema(nullable = true) RegularCertificateResponse certificate,
                                                  @Schema(nullable = true) RegularCertificateRequestResponse request) {

    public static RegularCertificateSubmissionResponse issued(RegularCertificateResponse certificate) {
        return new RegularCertificateSubmissionResponse(certificate, null);
    }

    public static RegularCertificateSubmissionResponse pending(RegularCertificateRequestResponse request) {
        return new RegularCertificateSubmissionResponse(null, request);
    }

    public boolean wasIssued() {
        return certificate != null;
    }
}
