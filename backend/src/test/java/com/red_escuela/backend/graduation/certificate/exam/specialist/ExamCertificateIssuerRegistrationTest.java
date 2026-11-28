package com.red_escuela.backend.graduation.certificate.exam.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceReader;
import com.red_escuela.backend.evaluation.api.ExamBoardRegistrationFinder;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateIssuer.ExamCertificateIssueCommand;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.certificate.specialist.VerifiableDocumentRegistry;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.graduation.documentsettings.specialist.DocumentIssuanceSettingsFinder;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExamCertificateIssuerRegistrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    private final ExamCertificateRepository repository = mock(ExamCertificateRepository.class);
    private final ExamCertificateTemplate template = mock(ExamCertificateTemplate.class);
    private final DocumentIssuanceSettingsFinder settings = mock(DocumentIssuanceSettingsFinder.class);
    private final VerifiableDocumentRegistry registry = mock(VerifiableDocumentRegistry.class);
    private final ExamCertificateIssuer issuer = new ExamCertificateIssuer(repository, template, mock(CertificatePdfRenderer.class),
            mock(CertificateFileStore.class), mock(CertificateVerificationUrl.class), mock(CertificateDigest.class),
            mock(StudentFinder.class), mock(CurrentActorFinder.class), settings, mock(AuditTrail.class),
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), mock(CertificateTemplateRenderSource.class),
            mock(ExamBoardRegistrationFinder.class), mock(ExamBoardAttendanceReader.class), registry);

    @Test
    void issuedExamCertificateIsRegisteredAsVerifiableUnderItsOwnCode() {
        when(settings.current()).thenReturn(new DocumentIssuanceSettingsResponse(5, 30, 30, 1, 1L));
        when(template.snapshotOf(any(), any(), any(), any())).thenReturn(mock(CertificateSnapshot.class));
        when(repository.saveAndFlush(any(ExamCertificate.class))).thenAnswer(call -> {
            ExamCertificate toSave = call.getArgument(0);
            return ExamCertificate.builder().id(77L).code(toSave.getCode()).pdfSha256("x").build();
        });

        issuer.issue(command());

        ArgumentCaptor<CertificateCode> code = ArgumentCaptor.forClass(CertificateCode.class);
        verify(registry).register(eq(CertificateDocumentType.EXAM_CERTIFICATE), eq(77L), code.capture());
        ArgumentCaptor<ExamCertificate> saved = ArgumentCaptor.forClass(ExamCertificate.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(code.getValue().value()).isEqualTo(saved.getValue().getCode());
    }

    private static ExamCertificateIssueCommand command() {
        return new ExamCertificateIssueCommand(1L, 2L, 3L, "Anatomía", LocalDate.of(2026, 11, 5), null, null,
                DocumentOrigin.ISSUED_BY_STAFF);
    }
}
