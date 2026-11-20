package com.red_escuela.backend.graduation.certificate.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import org.springframework.lang.Nullable;
import java.time.LocalDate;

public record EligibleGraduateResponse(
        Long enrollmentId,
        GraduationCertificateKind kind,
        Long studentId,
        String studentFullName,
        @Nullable String holderDocumentType,
        String documentNumber,
        String titleName,
        LocalDate graduationDate,
        GraduateEligibilityStatus status) {
}
