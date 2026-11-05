package com.red_escuela.backend.graduation.certificate.entity;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.SignatorySnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraduationCertificateTest {

    private final CertificateSnapshot snapshot = new CertificateSnapshot("PÉREZ, ANA", "DNI", "30123456", "el curso", "Marketing",
            null, null, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto",
            List.of(new SignatorySnapshot("Dirección", "Dirección académica")), "BUILT_IN_V1");

    @Test
    void issuedCertificateIsValid() {
        GraduationCertificate certificate = GraduationCertificate.issue(GraduationCertificateKind.COURSE, 42L, 7L,
                new CertificateCode("7QK3M9XH2VBN4T8R"), snapshot, "a".repeat(64), "7QK3M9XH2VBN4T8R.pdf",
                LocalDateTime.of(2026, 11, 5, 10, 0), 3L);

        assertThat(certificate.getStatus()).isEqualTo(GraduationCertificateStatus.VALID);
        assertThat(certificate.getCourseEnrollmentId()).isEqualTo(42L);
        assertThat(certificate.getProgramEnrollmentId()).isNull();
        assertThat(certificate.getGraduationDate()).isEqualTo(LocalDate.of(2026, 7, 15));
    }
}
