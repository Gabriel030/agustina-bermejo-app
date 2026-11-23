package com.red_escuela.backend.graduation.procedure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.engine.BuiltInCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.exception.CertificatePdfIntegrityMismatchException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateRevokedException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDocumentAuditRecorder;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.graduation.procedure.specialist.GraduationApprovedProcedureSource;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GraduationApprovedProcedureSourceTest {

    private static final String CODE = "7QK3M9XH2VBN4T8R5W6YZ0A1B2";
    private static final Long OWNER = 7L;
    private static final Long STRANGER = 8L;

    private final CertificateSnapshot snapshot = new CertificateSnapshot("PEREZ, ANA", "DNI", "30123456", "la carrera", "Tec", null,
            1800, LocalDate.of(2026, 7, 15), LocalDate.of(2026, 11, 5), "Instituto", List.of(), "BUILT_IN_V1", null);
    private final GraduationCertificateRepository repository = mock(GraduationCertificateRepository.class);
    private final CertificateFileStore fileStore = mock(CertificateFileStore.class);
    private final CertificatePdfRenderer renderer = mock(CertificatePdfRenderer.class);
    private final CertificateDigest digest = mock(CertificateDigest.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private GraduationApprovedProcedureSource source;

    @BeforeEach
    void setUp() {
        GraduationCertificatePdfProvider provider = new GraduationCertificatePdfProvider(repository, fileStore, renderer,
                new BuiltInCertificateTemplate(), mock(CertificateTemplateRenderSource.class),
                new CertificateVerificationUrl("https://app.example"), digest, new CertificateDocumentAuditRecorder(auditTrail));
        source = new GraduationApprovedProcedureSource(repository, provider);
    }

    private GraduationCertificate certificate(GraduationCertificateStatus status) {
        return GraduationCertificate.builder().id(42L).code(CODE).kind(GraduationCertificateKind.PROGRAM).studentId(OWNER)
                .status(status).snapshot(snapshot).pdfPath("x.pdf").pdfSha256("stored").renderEngineVersion("1")
                .issuedAt(LocalDateTime.of(2026, 11, 5, 10, 0)).build();
    }

    private AuditRecord recordedAudit() {
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditTrail).record(captor.capture());
        return captor.getValue();
    }

    @Test
    void listShowsOnlyTheStudentsOwnDocumentsWithTheirValidityStatus() {
        when(repository.findByStudentIdOrderByIssuedAtDescIdDesc(OWNER)).thenReturn(List.of(certificate(GraduationCertificateStatus.VALID)));
        when(repository.findByStudentIdOrderByIssuedAtDescIdDesc(STRANGER)).thenReturn(List.of());

        List<ApprovedProcedureResponse> own = source.listOf(OWNER);

        assertThat(own).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(42L);
            assertThat(item.status()).isEqualTo(DocumentValidityStatus.VALID);
            assertThat(item.verifiable()).isTrue();
        });
        assertThat(source.listOf(STRANGER)).isEmpty();
    }

    @Test
    void revokedDocumentIsListedAsRevokedAndNotVerifiable() {
        when(repository.findByStudentIdOrderByIssuedAtDescIdDesc(OWNER)).thenReturn(List.of(certificate(GraduationCertificateStatus.REVOKED)));

        assertThat(source.listOf(OWNER)).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo(DocumentValidityStatus.REVOKED);
            assertThat(item.verifiable()).isFalse();
        });
    }

    @Test
    void anotherStudentGetsNotFoundAndNothingIsAudited() {
        when(repository.findById(42L)).thenReturn(Optional.of(certificate(GraduationCertificateStatus.VALID)));

        assertThatThrownBy(() -> source.pdfOf(42L, STRANGER)).isInstanceOf(GraduationCertificateNotFoundException.class);

        verify(auditTrail, never()).record(any());
    }

    @Test
    void revokedDocumentCannotBeRedownloaded() {
        when(repository.findById(42L)).thenReturn(Optional.of(certificate(GraduationCertificateStatus.REVOKED)));

        assertThatThrownBy(() -> source.pdfOf(42L, OWNER)).isInstanceOf(GraduationCertificateRevokedException.class);

        verify(auditTrail, never()).record(any());
    }

    @Test
    void redownloadAuditsTheDownloadAndDoesNotIssueAnything() {
        when(repository.findById(42L)).thenReturn(Optional.of(certificate(GraduationCertificateStatus.VALID)));
        when(fileStore.load("x.pdf")).thenReturn(Optional.of(new byte[] {9}));
        when(digest.sha256(any())).thenReturn("stored");

        ProcedureDocumentPdf pdf = source.pdfOf(42L, OWNER);

        assertThat(pdf.code()).isEqualTo(CODE);
        assertThat(pdf.content()).containsExactly(9);
        AuditRecord audit = recordedAudit();
        assertThat(audit.action()).isEqualTo(AuditAction.DOCUMENT_PDF_DOWNLOADED);
        assertThat(audit.entityId()).isEqualTo(42L);
        verify(repository, never()).save(any());
    }

    @Test
    void regeneratedPdfWithADifferentHashIsRejectedAndAuditedAsMismatch() {
        when(repository.findById(42L)).thenReturn(Optional.of(certificate(GraduationCertificateStatus.VALID)));
        when(fileStore.load("x.pdf")).thenReturn(Optional.empty());
        when(renderer.render(any(), any(), any(), any(), any())).thenReturn(new byte[] {1});
        when(digest.sha256(any())).thenReturn("other");

        assertThatThrownBy(() -> source.pdfOf(42L, OWNER)).isInstanceOf(CertificatePdfIntegrityMismatchException.class);

        assertThat(recordedAudit().action()).isEqualTo(AuditAction.DOCUMENT_PDF_HASH_MISMATCH);
    }
}
