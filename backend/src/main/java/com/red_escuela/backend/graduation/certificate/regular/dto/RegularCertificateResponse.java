package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.StandingBasis;

import java.time.LocalDateTime;

public record RegularCertificateResponse(Long id, String code, RegularityKind kind, Long enrollmentId,
                                         RegularCertificateStatus status, LocalDateTime issuedAt, LocalDateTime expiresAt,
                                         StandingBasis standingBasis, String billingModeAtIssue, String verificationUrl) {
}
