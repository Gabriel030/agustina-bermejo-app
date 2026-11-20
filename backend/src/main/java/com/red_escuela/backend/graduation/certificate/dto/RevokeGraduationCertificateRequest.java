package com.red_escuela.backend.graduation.certificate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RevokeGraduationCertificateRequest(@NotNull @Size(max = 500) String reason) {
}
