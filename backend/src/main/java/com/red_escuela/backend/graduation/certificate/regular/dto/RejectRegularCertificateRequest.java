package com.red_escuela.backend.graduation.certificate.regular.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RejectRegularCertificateRequest(@NotNull @Size(max = 500) String reason) {
}
