package com.red_escuela.backend.graduation.certificate.template.dto;

import org.springframework.lang.Nullable;

public record CertificateTemplateSigner(@Nullable String name, @Nullable String role, @Nullable Long signatureImageId) {
}
