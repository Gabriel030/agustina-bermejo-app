package com.red_escuela.backend.graduation.certificate.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GraduationCertificateResponse(
        Long id,
        String code,
        GraduationCertificateKind kind,
        GraduationCertificateStatus status,
        String holderFullName,
        String titleName,
        LocalDate graduationDate,
        LocalDateTime issuedAt,
        String verificationUrl) {
}
