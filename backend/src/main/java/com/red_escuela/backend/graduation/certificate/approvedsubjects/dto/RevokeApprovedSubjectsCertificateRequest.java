package com.red_escuela.backend.graduation.certificate.approvedsubjects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeApprovedSubjectsCertificateRequest(@NotBlank @Size(max = 500) String reason) {
}
