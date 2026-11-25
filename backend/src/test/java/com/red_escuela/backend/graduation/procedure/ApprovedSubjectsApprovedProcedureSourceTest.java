package com.red_escuela.backend.graduation.procedure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificatePdf;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.exception.ApprovedSubjectsCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist.ApprovedSubjectsCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExpiredException;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedSubjectsApprovedProcedureSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApprovedSubjectsApprovedProcedureSourceTest {

    private static final Long OWNER = 7L;
    private static final Long STRANGER = 8L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    private final ApprovedSubjectsCertificateRepository repository = mock(ApprovedSubjectsCertificateRepository.class);
    private final ApprovedSubjectsCertificatePdfProvider pdfProvider = mock(ApprovedSubjectsCertificatePdfProvider.class);
    private ApprovedSubjectsApprovedProcedureSource source;

    @BeforeEach
    void setUp() {
        source = new ApprovedSubjectsApprovedProcedureSource(repository, pdfProvider, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private ApprovedSubjectsCertificate certificate(long id, ApprovedSubjectsCertificateStatus status, LocalDateTime expiresAt) {
        ApprovedSubjectsSnapshot snapshot = new ApprovedSubjectsSnapshot("Ana", "DNI", "1", "Tecnicatura", "Instituto", List.of(), 1, 2,
                null, null, null, null, NOW.toLocalDate(), expiresAt.toLocalDate(), List.of(), "BUILT_IN_V1");
        return ApprovedSubjectsCertificate.builder().id(id).code("CODE" + id).studentId(OWNER).status(status).issuedAt(NOW.minusDays(1))
                .expiresAt(expiresAt).snapshot(snapshot).build();
    }

    @Test
    void documentTypeIsApprovedSubjects() {
        assertThat(source.documentType()).isEqualTo(CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE);
    }

    @Test
    void listMapsEachCertificateWithItsEffectiveStatus() {
        when(repository.findByStudentIdOrderByIssuedAtDesc(OWNER)).thenReturn(List.of(
                certificate(1, ApprovedSubjectsCertificateStatus.VALID, NOW.plusDays(5)),
                certificate(2, ApprovedSubjectsCertificateStatus.VALID, NOW),
                certificate(3, ApprovedSubjectsCertificateStatus.REVOKED, NOW.plusDays(5)),
                certificate(4, ApprovedSubjectsCertificateStatus.EXPIRED, NOW.minusDays(5))));

        List<ApprovedProcedureResponse> items = source.listOf(OWNER);

        assertThat(items).extracting(ApprovedProcedureResponse::status).containsExactly(DocumentValidityStatus.VALID,
                DocumentValidityStatus.EXPIRED, DocumentValidityStatus.REVOKED, DocumentValidityStatus.EXPIRED);
        assertThat(items).extracting(ApprovedProcedureResponse::verifiable).containsExactly(true, false, false, false);
        assertThat(items.get(0).documentType()).isEqualTo(CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE);
        assertThat(items.get(0).detail()).isEqualTo("Tecnicatura");
    }

    @Test
    void pdfOfAValidOwnCertificateDelegatesToTheProvider() {
        when(repository.findByIdAndStudentId(1L, OWNER))
                .thenReturn(Optional.of(certificate(1, ApprovedSubjectsCertificateStatus.VALID, NOW.plusDays(5))));
        when(pdfProvider.pdfOfOwn(1L, OWNER)).thenReturn(new ApprovedSubjectsCertificatePdf("CODE1", new byte[] {1, 2}));

        ProcedureDocumentPdf pdf = source.pdfOf(1L, OWNER);

        assertThat(pdf.code()).isEqualTo("CODE1");
        assertThat(pdf.content()).containsExactly(1, 2);
    }

    @Test
    void pdfOfAnExpiredCertificateConflictsAndNothingIsDownloaded() {
        when(repository.findByIdAndStudentId(2L, OWNER))
                .thenReturn(Optional.of(certificate(2, ApprovedSubjectsCertificateStatus.VALID, NOW)));

        assertThatThrownBy(() -> source.pdfOf(2L, OWNER)).isInstanceOf(CertificateExpiredException.class);
        verify(pdfProvider, never()).pdfOfOwn(anyLong(), anyLong());
    }

    @Test
    void pdfOfAnotherStudentsCertificateIsNotFound() {
        when(repository.findByIdAndStudentId(1L, STRANGER)).thenReturn(Optional.empty());
        when(pdfProvider.pdfOfOwn(1L, STRANGER)).thenThrow(new ApprovedSubjectsCertificateNotFoundException());

        assertThatThrownBy(() -> source.pdfOf(1L, STRANGER)).isInstanceOf(ApprovedSubjectsCertificateNotFoundException.class);
    }
}
