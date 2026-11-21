package com.red_escuela.backend.graduation.certificate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReissueGraduationCertificateRequest(@NotNull @Size(min = 10, max = 500) String reason) {
}
