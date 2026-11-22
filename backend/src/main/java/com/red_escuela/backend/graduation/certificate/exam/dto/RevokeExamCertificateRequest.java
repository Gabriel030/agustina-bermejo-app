package com.red_escuela.backend.graduation.certificate.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeExamCertificateRequest(@NotBlank @Size(max = 500) String reason) {
}
