package com.red_escuela.backend.graduation.certificate.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

public record CertificateTemplateContent(
        @Nullable @Size(max = 120) String instituteName,
        @Nullable Long logoImageId,
        @Nullable @Size(max = 80) String title,
        @Nullable @Size(max = 240) String introText,
        @Nullable @Size(max = 160) String approvalText,
        @Nullable @Size(max = 160) String dictationText,
        @Nullable @Size(max = 160) String qrLabel,
        @Nullable @Size(max = 240) String footerLegend,
        @Valid @Schema(nullable = true) CertificateTemplateSigner firstSigner,
        @Valid @Schema(nullable = true) CertificateTemplateSigner secondSigner,
        @Nullable Long signatory1Id,
        @Nullable Long signatory2Id) {
}
