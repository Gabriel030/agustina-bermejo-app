package com.red_escuela.backend.graduation.certificate.template.dto;

public record CertificateTemplateImageUpload(TemplateImagePurpose purpose, byte[] content, String contentType, String originalFilename) {
}
