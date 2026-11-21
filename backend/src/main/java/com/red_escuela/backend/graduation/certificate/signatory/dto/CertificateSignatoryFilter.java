package com.red_escuela.backend.graduation.certificate.signatory.dto;

public record CertificateSignatoryFilter(Boolean active, String query, SignatoryDocumentType documentType) {
}
