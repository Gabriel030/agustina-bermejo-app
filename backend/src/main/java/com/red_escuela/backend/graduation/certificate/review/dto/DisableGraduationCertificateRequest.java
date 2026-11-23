package com.red_escuela.backend.graduation.certificate.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DisableGraduationCertificateRequest(@NotNull @Size(min = 10, max = 500) String reason) {
}
