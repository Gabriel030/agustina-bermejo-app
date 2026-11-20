package com.red_escuela.backend.graduation.certificate.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import java.time.LocalDate;

public record GraduationCertificateFilter(
        GraduationCertificateKind kind,
        GraduationCertificateStatus status,
        String query,
        LocalDate issuedFrom,
        LocalDate issuedTo) {
}
