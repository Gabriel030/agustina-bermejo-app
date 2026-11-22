package com.red_escuela.backend.graduation.certificate.exam.dto;

import jakarta.validation.constraints.Pattern;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record IssueExamCertificateBatchRequest(@NotEmpty @Size(max = 300) List<@NotNull Long> registrationIds, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) RecipientType recipientType, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Pattern(regexp = "[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*") @Size(max = 80) String recipientText) {
}
