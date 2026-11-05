package com.red_escuela.backend.graduation.certificate.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import jakarta.validation.constraints.NotNull;

public record IssueGraduationCertificateRequest(@NotNull GraduationCertificateKind kind, @NotNull Long enrollmentId) {
}
