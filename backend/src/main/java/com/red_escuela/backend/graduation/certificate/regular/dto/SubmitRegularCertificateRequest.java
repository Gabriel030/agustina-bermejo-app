package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import jakarta.validation.constraints.NotNull;

public record SubmitRegularCertificateRequest(@NotNull RegularityKind kind, @NotNull Long enrollmentId) {
}
