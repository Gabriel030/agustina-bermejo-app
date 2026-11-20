package com.red_escuela.backend.graduation.certificate.template.dto;

import jakarta.validation.constraints.NotNull;

public record PreviewCertificateTemplateRequest(@NotNull TemplatePreviewSource source) {
}
