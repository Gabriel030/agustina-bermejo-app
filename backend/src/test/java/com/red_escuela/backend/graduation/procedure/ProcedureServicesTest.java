package com.red_escuela.backend.graduation.procedure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureDocumentNotFoundException;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedProcedureSource;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedProcedureSources;
import com.red_escuela.backend.graduation.procedure.specialist.ProcedureEligibilityStrategies;
import com.red_escuela.backend.graduation.procedure.specialist.ProcedureEligibilityStrategy;
import com.red_escuela.backend.graduation.procedure.usecase.GetApprovedProcedurePdfService;
import com.red_escuela.backend.graduation.procedure.usecase.GetProcedureEligibilityService;
import com.red_escuela.backend.graduation.procedure.usecase.ListApprovedProceduresService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcedureServicesTest {

    private static final Long STUDENT_ID = 7L;

    private CurrentActorFinder actor;

    @BeforeEach
    void setUp() {
        actor = mock(CurrentActorFinder.class);
        when(actor.currentStudentId()).thenReturn(STUDENT_ID);
    }

    private static ProcedureEligibilityStrategy strategy(StudentProcedureType type, ProcedureEligibilityResponse response) {
        ProcedureEligibilityStrategy strategy = mock(ProcedureEligibilityStrategy.class);
        when(strategy.type()).thenReturn(type);
        when(strategy.eligibilityOf(STUDENT_ID, 3L, null)).thenReturn(response);
        return strategy;
    }

    private static ApprovedProcedureSource source(CertificateDocumentType type, List<ApprovedProcedureResponse> items) {
        ApprovedProcedureSource source = mock(ApprovedProcedureSource.class);
        when(source.documentType()).thenReturn(type);
        when(source.listOf(STUDENT_ID)).thenReturn(items);
        return source;
    }

    private static ApprovedProcedureResponse item(CertificateDocumentType type, Long id, LocalDateTime issuedAt,
                                                  DocumentValidityStatus status) {
        return new ApprovedProcedureResponse(type, id, "t", null, issuedAt, null, status, DocumentOrigin.SELF_SERVICE,
                status == DocumentValidityStatus.VALID);
    }

    @Test
    void dispatcherDelegatesToTheStrategyRegisteredForTheType() {
        ProcedureEligibilityResponse regular = new ProcedureEligibilityResponse(StudentProcedureType.REGULAR_STUDENT_CERTIFICATE,
                true, 3L, "ok", List.of());
        ProcedureEligibilityResponse exam = new ProcedureEligibilityResponse(StudentProcedureType.EXAM_CERTIFICATE,
                false, 3L, "no", List.of());
        GetProcedureEligibilityService service = new GetProcedureEligibilityService(new ProcedureEligibilityStrategies(List.of(
                strategy(StudentProcedureType.REGULAR_STUDENT_CERTIFICATE, regular), strategy(StudentProcedureType.EXAM_CERTIFICATE, exam))), actor);

        assertThat(service.eligibilityOf(StudentProcedureType.REGULAR_STUDENT_CERTIFICATE, 3L, null)).isSameAs(regular);
        assertThat(service.eligibilityOf(StudentProcedureType.EXAM_CERTIFICATE, 3L, null)).isSameAs(exam);
    }

    @Test
    void typeWithoutStrategyAnswersUnavailableInsteadOfFailing() {
        GetProcedureEligibilityService service = new GetProcedureEligibilityService(new ProcedureEligibilityStrategies(List.of()), actor);

        ProcedureEligibilityResponse response = service.eligibilityOf(StudentProcedureType.APPROVED_SUBJECTS_CERTIFICATE, 3L, null);

        assertThat(response.eligible()).isFalse();
        assertThat(response.type()).isEqualTo(StudentProcedureType.APPROVED_SUBJECTS_CERTIFICATE);
        assertThat(response.summary()).isEqualTo("Este trámite todavía no está disponible");
        assertThat(response.reasons()).isEmpty();
    }

    @Test
    void twoStrategiesForTheSameTypeFailAtStartup() {
        ProcedureEligibilityResponse response = new ProcedureEligibilityResponse(StudentProcedureType.EXAM_CERTIFICATE, true, 3L, "ok", List.of());

        assertThatThrownBy(() -> new ProcedureEligibilityStrategies(List.of(
                strategy(StudentProcedureType.EXAM_CERTIFICATE, response), strategy(StudentProcedureType.EXAM_CERTIFICATE, response))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approvedListMergesEverySourceNewestFirstKeepingEachStatus() {
        ApprovedProcedureResponse oldRevoked = item(CertificateDocumentType.GRADUATION_CERTIFICATE, 1L, LocalDateTime.of(2026, 1, 1, 10, 0),
                DocumentValidityStatus.REVOKED);
        ApprovedProcedureResponse expired = item(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, 2L, LocalDateTime.of(2026, 5, 1, 10, 0),
                DocumentValidityStatus.EXPIRED);
        ApprovedProcedureResponse valid = item(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, 3L, LocalDateTime.of(2026, 9, 1, 10, 0),
                DocumentValidityStatus.VALID);
        ListApprovedProceduresService service = new ListApprovedProceduresService(new ApprovedProcedureSources(List.of(
                source(CertificateDocumentType.GRADUATION_CERTIFICATE, List.of(oldRevoked)),
                source(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, List.of(expired, valid)))), actor);

        assertThat(service.listApproved()).containsExactly(valid, expired, oldRevoked);
    }

    @Test
    void approvedListIsEmptyWhenNoSourceHasDocuments() {
        ListApprovedProceduresService service = new ListApprovedProceduresService(new ApprovedProcedureSources(List.of()), actor);

        assertThat(service.listApproved()).isEmpty();
    }

    @Test
    void pdfIsServedByTheSourceOfTheDocumentTypeForTheCurrentStudent() {
        ProcedureDocumentPdf pdf = new ProcedureDocumentPdf("CODE", new byte[] {1});
        ApprovedProcedureSource regular = source(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, List.of());
        when(regular.pdfOf(5L, STUDENT_ID)).thenReturn(pdf);
        GetApprovedProcedurePdfService service = new GetApprovedProcedurePdfService(new ApprovedProcedureSources(List.of(regular)), actor);

        assertThat(service.pdfOf(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, 5L)).isSameAs(pdf);
    }

    @Test
    void pdfOfATypeWithoutSourceIsNotFound() {
        GetApprovedProcedurePdfService service = new GetApprovedProcedurePdfService(new ApprovedProcedureSources(List.of()), actor);

        assertThatThrownBy(() -> service.pdfOf(CertificateDocumentType.EXAM_CERTIFICATE, 5L))
                .isInstanceOf(ProcedureDocumentNotFoundException.class);
    }
}
