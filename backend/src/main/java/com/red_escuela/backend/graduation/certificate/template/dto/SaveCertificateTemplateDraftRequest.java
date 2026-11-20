package com.red_escuela.backend.graduation.certificate.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SaveCertificateTemplateDraftRequest(@NotNull @Valid CertificateTemplateContent content) {
}
