package com.red_escuela.backend.graduation.certificate.template.dto;

public record CertificateTemplateImageResponse(
        Long id,
        TemplateImagePurpose purpose,
        String contentType,
        long sizeBytes,
        int widthPixels,
        int heightPixels) {
}
