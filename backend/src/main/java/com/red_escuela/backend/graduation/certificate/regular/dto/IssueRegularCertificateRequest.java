package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IssueRegularCertificateRequest(@NotNull RegularityKind kind, @NotNull Long enrollmentId,
                                             boolean standingConfirmed, @Size(max = 500) String standingEvidence) {
}
