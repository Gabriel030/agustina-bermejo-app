package com.red_escuela.backend.graduation.certificate.exam.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateNotRevocableException;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExamCertificateRevokerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    private final ExamCertificateRepository repository = mock(ExamCertificateRepository.class);
    private final ExamCertificateResponses responses = mock(ExamCertificateResponses.class);
    private final CurrentActorFinder actor = mock(CurrentActorFinder.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private final ExamCertificateRevoker revoker = new ExamCertificateRevoker(repository, responses, actor, auditTrail,
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private ExamCertificate certificate(DocumentValidityStatus status) {
        ExamCertificate certificate = ExamCertificate.builder().id(5L).status(status).build();
        when(repository.findById(5L)).thenReturn(Optional.of(certificate));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return certificate;
    }

    @Test
    void validCertificateIsRevokedWithTrimmedReasonAndAudited() {
        ExamCertificate certificate = certificate(DocumentValidityStatus.VALID);
        when(actor.currentPersonId()).thenReturn(77L);

        revoker.revoke(5L, "  Emitida por error  ");

        assertThat(certificate.getStatus()).isEqualTo(DocumentValidityStatus.REVOKED);
        assertThat(certificate.getRevocationReason()).isEqualTo("Emitida por error");
        assertThat(certificate.getRevokedById()).isEqualTo(77L);
        assertThat(certificate.getRevokedAt()).isEqualTo(NOW);
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditTrail).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.EXAM_CERTIFICATE_REVOKED);
    }

    @Test
    void alreadyRevokedOrExpiredCertificateCannotBeRevoked() {
        certificate(DocumentValidityStatus.REVOKED);

        assertThatThrownBy(() -> revoker.revoke(5L, "x")).isInstanceOf(ExamCertificateNotRevocableException.class);
        verify(auditTrail, never()).record(any());
    }

    @Test
    void unknownCertificateIsNotFound() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> revoker.revoke(5L, "x")).isInstanceOf(ExamCertificateNotFoundException.class);
    }
}
