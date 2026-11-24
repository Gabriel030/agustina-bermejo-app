package com.red_escuela.backend.graduation.certificate.approvedsubjects.entity;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshotFactory;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovedSubjectsCertificateTest {

    private final LocalDateTime issuedAt = LocalDateTime.of(2026, 11, 5, 10, 0);

    private ApprovedSubjectsCertificate certificate() {
        ApprovedSubjectsSnapshot snapshot = new ApprovedSubjectsSnapshotFactory().create(
                new ApprovedSubjectsSnapshotFactory.Input("PÉREZ, ANA", "DNI", "30123456", "Carrera", "Instituto", List.of(), 1,
                        null, LocalDate.of(2026, 11, 5), List.of()));
        return ApprovedSubjectsCertificate.createValid(1L, 2L, new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2"), snapshot,
                "a".repeat(64), "1", "REG-1", issuedAt, 30, 3L);
    }

    @Test
    void issuedCertificateIsValidForThirtyDays() {
        ApprovedSubjectsCertificate certificate = certificate();

        assertThat(certificate.getStatus()).isEqualTo(ApprovedSubjectsCertificateStatus.VALID);
        assertThat(certificate.getExpiresAt()).isEqualTo(issuedAt.plusDays(30));
        assertThat(certificate.isExpiredAt(issuedAt.plusDays(30).minusSeconds(1))).isFalse();
        assertThat(certificate.isExpiredAt(issuedAt.plusDays(30))).isTrue();
    }

    @Test
    void expireOnlyAffectsValidCertificates() {
        ApprovedSubjectsCertificate certificate = certificate();
        certificate.revoke("motivo", 9L, issuedAt.plusDays(1));

        certificate.expire();

        assertThat(certificate.getStatus()).isEqualTo(ApprovedSubjectsCertificateStatus.REVOKED);
        assertThat(certificate.isRevocable()).isFalse();
    }

    @Test
    void expireMovesValidToExpired() {
        ApprovedSubjectsCertificate certificate = certificate();

        certificate.expire();

        assertThat(certificate.getStatus()).isEqualTo(ApprovedSubjectsCertificateStatus.EXPIRED);
    }
}
