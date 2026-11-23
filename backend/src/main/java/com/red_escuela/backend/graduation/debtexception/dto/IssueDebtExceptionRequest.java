package com.red_escuela.backend.graduation.debtexception.dto;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record IssueDebtExceptionRequest(
        @NotNull @Positive Long studentId,
        @NotNull CertificateDocumentType documentType,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long enrollmentId,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long registrationId,
        @NotBlank @Size(min = 20, max = 500)
        @Pattern(regexp = "(?=[\\s\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000\\uFEFF]*[^\\s\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000\\uFEFF].{18,}[^\\s\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000\\uFEFF][\\s\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000\\uFEFF]*$)[^\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]*") String reason,
        @Min(1) @Max(90) int validityDays) {
}
