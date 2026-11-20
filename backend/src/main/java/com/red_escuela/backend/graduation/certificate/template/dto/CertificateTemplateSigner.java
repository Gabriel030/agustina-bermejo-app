package com.red_escuela.backend.graduation.certificate.template.dto;

import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record CertificateTemplateSigner(@Nullable @Size(max = 80) String name, @Nullable @Size(max = 80) String role, @Nullable Long signatureImageId) {
}
