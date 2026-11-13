package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IssueRegularCertificateRequest(@NotNull RegularityKind kind, @NotNull Long enrollmentId,
                                             boolean standingConfirmed, @Size(max = 500) @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String standingEvidence) {
}
