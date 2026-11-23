package com.red_escuela.backend.graduation.certificate.approvedsubjects.dto;

import jakarta.validation.constraints.NotNull;

public record RequestApprovedSubjectsCertificateRequest(@NotNull Long enrollmentId) {
}
