package com.red_escuela.backend.graduation.certificate.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import java.time.LocalDate;

public record EligibleGraduatesFilter(
        GraduationCertificateKind kind,
        String query,
        LocalDate graduatedFrom,
        LocalDate graduatedTo) {
}
