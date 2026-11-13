package com.red_escuela.backend.graduation.certificate.regular.dto;

public record RegularCertificateSubmissionResponse(RegularCertificateResponse certificate, RegularCertificateRequestResponse request) {

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
