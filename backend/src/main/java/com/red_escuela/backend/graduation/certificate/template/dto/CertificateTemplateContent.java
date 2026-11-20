package com.red_escuela.backend.graduation.certificate.template.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

public record CertificateTemplateContent(
        @Nullable String instituteName,
        @Nullable Long logoImageId,
        @Nullable String title,
        @Nullable String introText,
        @Nullable String approvalText,
        @Nullable String dictationText,
        @Nullable String qrLabel,
        @Nullable String footerLegend,
        @Schema(nullable = true) CertificateTemplateSigner firstSigner,
        @Schema(nullable = true) CertificateTemplateSigner secondSigner) {
}
