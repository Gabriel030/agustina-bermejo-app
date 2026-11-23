package com.red_escuela.backend.graduation.certificate.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.lang.Nullable;

public record SaveCertificateTemplateDraftRequest(
        @NotNull @Valid CertificateTemplateContent content,
        @Nullable @Positive Integer basedOnVersionNumber) {
}
