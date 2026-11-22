package com.red_escuela.backend.graduation.procedure.dto;

import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GenerateOwnDocumentRequest(
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long enrollmentId,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long registrationId,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) RecipientType recipientType,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Pattern(regexp = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*") @Size(max = 80) String recipientText) {
}
