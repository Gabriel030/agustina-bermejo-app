package com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.exception.ApprovedSubjectsCertificateNotAvailableException;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.exception.ApprovedSubjectsCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsActor;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsAuditLog;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsCertificateStore;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshotFactory;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovedSubjectsCertificateRevokerTest {

    private static final Long PERSON_ID = 77L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneOffset.UTC);

    private final Map<Long, ApprovedSubjectsCertificate> stored = new HashMap<>();
    private final List<String> audited = new ArrayList<>();
    private final List<ApprovedSubjectsCertificate> saved = new ArrayList<>();
    private ApprovedSubjectsCertificateRevoker service;

    @BeforeEach
    void setUp() {
        ApprovedSubjectsCertificateStore store = new ApprovedSubjectsCertificateStore() {
            public Optional<ApprovedSubjectsCertificate> findById(Long id) {
                return Optional.ofNullable(stored.get(id));
            }

            public ApprovedSubjectsCertificate save(ApprovedSubjectsCertificate certificate) {
                saved.add(certificate);
                return certificate;
            }
        };
        ApprovedSubjectsActor actor = new ApprovedSubjectsActor() {
            public Long currentStudentId() {
                return 1L;
            }

            public Long currentPersonId() {
                return PERSON_ID;
            }
        };
        ApprovedSubjectsAuditLog auditLog = (certificateId, reason) -> audited.add(certificateId + ":" + reason);
        service = new ApprovedSubjectsCertificateRevoker(store, certificate -> response(), actor, auditLog, CLOCK);
    }

    @Test
    void validCertificateIsRevokedWithActorTimeAndTrimmedReason() {
        ApprovedSubjectsCertificate certificate = validCertificate();
        stored.put(5L, certificate);

        service.revoke(5L, "  emitida por error  ");

        assertThat(certificate.getStatus()).isEqualTo(ApprovedSubjectsCertificateStatus.REVOKED);
        assertThat(certificate.getRevocationReason()).isEqualTo("emitida por error");
        assertThat(certificate.getRevokedById()).isEqualTo(PERSON_ID);
        assertThat(certificate.getRevokedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(saved).containsExactly(certificate);
        assertThat(audited).containsExactly("5:emitida por error");
    }

    @Test
    void alreadyRevokedCertificateIsNotAvailableAndNothingIsSavedOrAudited() {
        ApprovedSubjectsCertificate certificate = validCertificate();
        certificate.revoke("antes", 1L, LocalDateTime.now(CLOCK).minusDays(1));
        stored.put(5L, certificate);

        assertThatThrownBy(() -> service.revoke(5L, "otra vez"))
                .isInstanceOf(ApprovedSubjectsCertificateNotAvailableException.class);
        assertThat(saved).isEmpty();
        assertThat(audited).isEmpty();
        assertThat(certificate.getRevocationReason()).isEqualTo("antes");
    }

    @Test
    void expiredCertificateIsNotAvailable() {
        ApprovedSubjectsCertificate certificate = validCertificate();
        certificate.expire();
        stored.put(5L, certificate);

        assertThatThrownBy(() -> service.revoke(5L, "motivo"))
                .isInstanceOf(ApprovedSubjectsCertificateNotAvailableException.class);
        assertThat(saved).isEmpty();
    }

    @Test
    void missingCertificateIsNotFound() {
        assertThatThrownBy(() -> service.revoke(99L, "motivo"))
                .isInstanceOf(ApprovedSubjectsCertificateNotFoundException.class);
        assertThat(saved).isEmpty();
        assertThat(audited).isEmpty();
    }

    private static ApprovedSubjectsCertificate validCertificate() {
        LocalDateTime issuedAt = LocalDateTime.of(2026, 11, 5, 10, 0);
        ApprovedSubjectsSnapshot snapshot = new ApprovedSubjectsSnapshotFactory().create(
                new ApprovedSubjectsSnapshotFactory.Input("PÉREZ, ANA", "DNI", "30123456", "Carrera", "Instituto", List.of(), 1,
                        null, LocalDate.of(2026, 11, 5), 30, List.of()));
        return ApprovedSubjectsCertificate.createValid(1L, 2L, new CertificateCode("7QK3M9XH2VBN4T8R5W6YZ0A1B2"), snapshot,
                "a".repeat(64), "1", "REG-1", issuedAt, 30, 3L);
    }

    private static ApprovedSubjectsCertificateResponse response() {
        return new ApprovedSubjectsCertificateResponse(5L, "CODE", 2L, "Ana", "Carrera", 1L, null, null, null, null, 0, 1, 0, "url");
    }
}
