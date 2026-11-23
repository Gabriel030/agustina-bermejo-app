package com.red_escuela.backend.graduation.debtexception.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.graduation.api.DebtExceptionDocument;
import com.red_escuela.backend.graduation.api.DebtExceptionDocumentIssuer;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionPdf;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
import com.red_escuela.backend.graduation.debtexception.entity.DocumentDebtException;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionAlreadyUsedException;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionExpiredException;
import com.red_escuela.backend.graduation.debtexception.exception.DebtExceptionNotFoundException;
import com.red_escuela.backend.graduation.debtexception.repository.DebtExceptionState;
import com.red_escuela.backend.graduation.debtexception.repository.DocumentDebtExceptionRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DebtExceptionDownloaderTest {

    private static final Long ID = 5L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    private DocumentDebtExceptionRepository repository;
    private DebtExceptionDocumentIssuers issuers;
    private DebtExceptionDocumentIssuer issuer;
    private DebtExceptionAuditRecorder auditRecorder;
    private DebtExceptionDownloader downloader;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentDebtExceptionRepository.class);
        issuers = mock(DebtExceptionDocumentIssuers.class);
        issuer = mock(DebtExceptionDocumentIssuer.class);
        auditRecorder = mock(DebtExceptionAuditRecorder.class);
        CurrentActorFinder actorFinder = mock(CurrentActorFinder.class);
        when(actorFinder.currentPersonId()).thenReturn(9L);
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        downloader = new DebtExceptionDownloader(repository, issuers, auditRecorder, actorFinder, clock);
        when(issuers.forType(CertificateDocumentType.EXAM_CERTIFICATE)).thenReturn(Optional.of(issuer));
    }

    @Test
    void missingExceptionIsNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> downloader.download(ID)).isInstanceOf(DebtExceptionNotFoundException.class);
    }

    @Test
    void typeWithoutIssuerIsPendingAndNothingIsConsumed() {
        DocumentDebtException exception = exception(DebtExceptionStatus.ACTIVE, NOW.plusDays(1));
        when(exception.getDocumentType()).thenReturn(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE);
        when(issuers.forType(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> downloader.download(ID)).isInstanceOf(DocumentOperationPendingException.class);
        verify(repository, never()).markUsedIfActive(anyLong(), any(), anyLong());
    }

    @Test
    void alreadyUsedExceptionIsRejectedWithoutTryingToConsume() {
        exception(DebtExceptionStatus.USED, NOW.plusDays(1));

        assertThatThrownBy(() -> downloader.download(ID)).isInstanceOf(DebtExceptionAlreadyUsedException.class);
        verify(repository, never()).markUsedIfActive(anyLong(), any(), anyLong());
    }

    @Test
    void activeExceptionPastItsExpiryIsExpired() {
        exception(DebtExceptionStatus.ACTIVE, NOW);

        assertThatThrownBy(() -> downloader.download(ID)).isInstanceOf(DebtExceptionExpiredException.class);
        verify(repository, never()).markUsedIfActive(anyLong(), any(), anyLong());
    }

    @Test
    void loserOfTheRaceWhoSeesUsedGetsAlreadyUsed() {
        exception(DebtExceptionStatus.ACTIVE, NOW.plusDays(1));
        when(repository.markUsedIfActive(ID, NOW, 9L)).thenReturn(0);
        givenStateAfterLostRace(DebtExceptionStatus.USED);

        assertThatThrownBy(() -> downloader.download(ID)).isInstanceOf(DebtExceptionAlreadyUsedException.class);
        verify(issuer, never()).render(any());
    }

    @Test
    void loserOfTheRaceWhoSeesExpiredGetsExpired() {
        exception(DebtExceptionStatus.ACTIVE, NOW.plusDays(1));
        when(repository.markUsedIfActive(ID, NOW, 9L)).thenReturn(0);
        givenStateAfterLostRace(DebtExceptionStatus.EXPIRED);

        assertThatThrownBy(() -> downloader.download(ID)).isInstanceOf(DebtExceptionExpiredException.class);
    }

    @Test
    void loserOfTheRaceWhoSeesAStillActiveRowGetsExpired() {
        exception(DebtExceptionStatus.ACTIVE, NOW.plusDays(1));
        when(repository.markUsedIfActive(ID, NOW, 9L)).thenReturn(0);
        givenStateAfterLostRace(DebtExceptionStatus.ACTIVE);

        assertThatThrownBy(() -> downloader.download(ID)).isInstanceOf(DebtExceptionExpiredException.class);
    }

    @Test
    void loserOfTheRaceWhenTheRowVanishedIsNotFound() {
        exception(DebtExceptionStatus.ACTIVE, NOW.plusDays(1));
        when(repository.markUsedIfActive(ID, NOW, 9L)).thenReturn(0);
        when(repository.findStateById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> downloader.download(ID)).isInstanceOf(DebtExceptionNotFoundException.class);
    }

    @Test
    void winnerRendersAndAuditsTheDownload() {
        exception(DebtExceptionStatus.ACTIVE, NOW.plusDays(1));
        when(repository.markUsedIfActive(ID, NOW, 9L)).thenReturn(1);
        when(issuer.render(any())).thenReturn(new DebtExceptionDocument("EXC-5", new byte[] {1}, "documentId=5"));

        DebtExceptionPdf pdf = downloader.download(ID);

        assertThat(pdf.code()).isEqualTo("EXC-5");
        verify(auditRecorder).downloaded(ID, "EXAM_CERTIFICATE", 3L, "documentId=5");
    }

    @Test
    void failedRenderDoesNotAuditTheDownload() {
        exception(DebtExceptionStatus.ACTIVE, NOW.plusDays(1));
        when(repository.markUsedIfActive(ID, NOW, 9L)).thenReturn(1);
        when(issuer.render(any())).thenThrow(new IllegalStateException("render failed"));

        assertThatThrownBy(() -> downloader.download(ID)).isInstanceOf(IllegalStateException.class);
        verify(auditRecorder, never()).downloaded(anyLong(), any(), anyLong(), any());
    }

    private DocumentDebtException exception(DebtExceptionStatus status, LocalDateTime expiresAt) {
        DocumentDebtException exception = mock(DocumentDebtException.class);
        when(exception.getId()).thenReturn(ID);
        when(exception.getStudentId()).thenReturn(3L);
        when(exception.getDocumentType()).thenReturn(CertificateDocumentType.EXAM_CERTIFICATE);
        when(exception.getStatus()).thenReturn(status);
        when(exception.getExpiresAt()).thenReturn(expiresAt);
        when(repository.findById(ID)).thenReturn(Optional.of(exception));
        return exception;
    }

    private void givenStateAfterLostRace(DebtExceptionStatus status) {
        DebtExceptionState state = mock(DebtExceptionState.class);
        when(state.getStatus()).thenReturn(status);
        when(repository.findStateById(ID)).thenReturn(Optional.of(state));
    }
}
