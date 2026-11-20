package com.red_escuela.backend.graduation.certificate.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import java.time.LocalDate;

public record EligibleGraduateResponse(
        Long enrollmentId,
        GraduationCertificateKind kind,
        Long studentId,
        String studentFullName,
        String documentNumber,
        String titleName,
        LocalDate graduationDate,
        GraduateEligibilityStatus status) {
}
