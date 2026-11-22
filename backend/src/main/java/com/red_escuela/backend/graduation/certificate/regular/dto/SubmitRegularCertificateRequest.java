package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitRegularCertificateRequest(
        @NotNull RegularityKind kind,
        @NotNull Long enrollmentId,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) RecipientType recipientType, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Pattern(regexp = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*") @Size(max = 80) String recipientText) {

    public SubmitRegularCertificateRequest(RegularityKind kind, Long enrollmentId) {
        this(kind, enrollmentId, null, null);
    }
}
