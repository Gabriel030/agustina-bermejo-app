package com.red_escuela.backend.graduation.procedure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RequestApprovedSubjectsCertificateRequest;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsEligibilityInput;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsEligibilityFacts;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsCertificateRequests;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentType;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityCode;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureDocumentNotFoundException;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureNotEligibleException;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedSubjectsOwnEnrollmentResolver;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedSubjectsProcedureEligibility;
import com.red_escuela.backend.graduation.procedure.specialist.ExamRegistrationCertificateIssuer;
import com.red_escuela.backend.graduation.procedure.specialist.OwnProgramEnrollmentResolver;
import com.red_escuela.backend.graduation.procedure.specialist.OwnRegularDocumentIssuer;
import com.red_escuela.backend.graduation.procedure.usecase.GenerateOwnDocumentService;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.module.BillingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApprovedSubjectsProcedureWiringTest {

    private static final Long STUDENT_ID = 7L;
    private static final Long ENROLLMENT_ID = 3L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    private CurrentActorFinder actor;
    private OwnProgramEnrollmentResolver resolver;
    private ApprovedSubjectsCertificateRequests requestUseCase;
    private ApprovedSubjectsEligibilityFacts facts;
    private Clock clock;

    @BeforeEach
    void setUp() {
        actor = mock(CurrentActorFinder.class);
        when(actor.currentStudentId()).thenReturn(STUDENT_ID);
        resolver = mock(OwnProgramEnrollmentResolver.class);
        requestUseCase = mock(ApprovedSubjectsCertificateRequests.class);
        facts = mock(ApprovedSubjectsEligibilityFacts.class);
        clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    private ApprovedSubjectsCertificateResponse issued() {
        return new ApprovedSubjectsCertificateResponse(55L, "CODE-55", STUDENT_ID, "Alumno", "Carrera", ENROLLMENT_ID,
                DocumentValidityStatus.VALID, NOW, NOW.plusDays(30), null, 2, 4, 50, "https://verify/CODE-55");
    }

    private ApprovedSubjectsEligibilityInput input(BillingMode mode, boolean debt, boolean regular, long issuedToday, int approved) {
        return new ApprovedSubjectsEligibilityInput(mode, debt, regular, issuedToday, 1, approved, true);
    }

    private ProcedureEligibilityResponse eligibility(ApprovedSubjectsEligibilityInput input) {
        when(resolver.resolve(STUDENT_ID, null)).thenReturn(new OwnProgramEnrollmentResolver.Resolution(Optional.of(ENROLLMENT_ID), false));
        when(facts.inputFor(any(), any(), any())).thenReturn(input);
        return new ApprovedSubjectsProcedureEligibility(resolver, facts, clock).eligibilityOf(STUDENT_ID, null, null);
    }

    @Test
    void generateDispatchesApprovedSubjectsToTheUseCaseAndMapsTheResult() {
        ApprovedSubjectsOwnEnrollmentResolver enrollmentResolver = new ApprovedSubjectsOwnEnrollmentResolver(actor, resolver);
        when(resolver.resolve(STUDENT_ID, null)).thenReturn(new OwnProgramEnrollmentResolver.Resolution(Optional.of(ENROLLMENT_ID), false));
        when(requestUseCase.request(new RequestApprovedSubjectsCertificateRequest(ENROLLMENT_ID))).thenReturn(issued());
        GenerateOwnDocumentService service = new GenerateOwnDocumentService(mock(OwnRegularDocumentIssuer.class),
                mock(ExamRegistrationCertificateIssuer.class), enrollmentResolver, requestUseCase);

        OwnDocumentResponse response = service.generate(OwnDocumentType.APPROVED_SUBJECTS_CERTIFICATE, null);

        assertThat(response.documentType()).isEqualTo(CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE);
        assertThat(response.id()).isEqualTo(55L);
        assertThat(response.code()).isEqualTo("CODE-55");
        assertThat(response.status()).isEqualTo(DocumentValidityStatus.VALID);
        assertThat(response.verificationUrl()).isEqualTo("https://verify/CODE-55");
    }

    @Test
    void generateWithoutAResolvableEnrollmentIsNotEligibleAndNothingIsRequested() {
        when(resolver.resolve(STUDENT_ID, null)).thenReturn(new OwnProgramEnrollmentResolver.Resolution(Optional.empty(), true));
        GenerateOwnDocumentService service = new GenerateOwnDocumentService(mock(OwnRegularDocumentIssuer.class),
                mock(ExamRegistrationCertificateIssuer.class), new ApprovedSubjectsOwnEnrollmentResolver(actor, resolver), requestUseCase);

        assertThatThrownBy(() -> service.generate(OwnDocumentType.APPROVED_SUBJECTS_CERTIFICATE, null))
                .isInstanceOf(ProcedureNotEligibleException.class);
        verify(requestUseCase, never()).request(any());
    }

    @Test
    void generateWithAnotherStudentsEnrollmentIsNotFoundAndNothingIsRequested() {
        when(resolver.resolve(STUDENT_ID, 99L)).thenThrow(new ProcedureDocumentNotFoundException());
        GenerateOwnDocumentService service = new GenerateOwnDocumentService(mock(OwnRegularDocumentIssuer.class),
                mock(ExamRegistrationCertificateIssuer.class), new ApprovedSubjectsOwnEnrollmentResolver(actor, resolver), requestUseCase);

        assertThatThrownBy(() -> service.generate(OwnDocumentType.APPROVED_SUBJECTS_CERTIFICATE,
                new GenerateOwnDocumentRequest(99L, null, null, null))).isInstanceOf(ProcedureDocumentNotFoundException.class);
        verify(requestUseCase, never()).request(any());
    }

    @Test
    void eligibilityIsTrueWhenThePolicyHasNoBlockers() {
        ProcedureEligibilityResponse response = eligibility(input(BillingMode.OFF, false, true, 0, 2));

        assertThat(response.type()).isEqualTo(StudentProcedureType.APPROVED_SUBJECTS_CERTIFICATE);
        assertThat(response.eligible()).isTrue();
        assertThat(response.enrollmentId()).isEqualTo(ENROLLMENT_ID);
        assertThat(response.reasons()).isEmpty();
    }

    @Test
    void eligibilityMapsEachPolicyBlockerToItsCode() {
        assertThat(eligibility(input(BillingMode.OFF, false, false, 0, 2)).reasons())
                .extracting(reason -> reason.code()).containsExactly(ProcedureIneligibilityCode.NOT_REGULAR_STUDENT);
        assertThat(eligibility(input(BillingMode.MIRROR, false, true, 0, 2)).reasons())
                .extracting(reason -> reason.code()).containsExactly(ProcedureIneligibilityCode.PENDING_PAYMENTS);
        assertThat(eligibility(input(BillingMode.FULL, true, true, 0, 2)).reasons())
                .extracting(reason -> reason.code()).containsExactly(ProcedureIneligibilityCode.PENDING_PAYMENTS);
        assertThat(eligibility(input(BillingMode.OFF, false, true, 1, 2)).reasons())
                .extracting(reason -> reason.code()).containsExactly(ProcedureIneligibilityCode.ISSUANCE_LIMIT_REACHED);
        assertThat(eligibility(input(BillingMode.OFF, false, true, 0, 0)).reasons())
                .extracting(reason -> reason.code()).containsExactly(ProcedureIneligibilityCode.NO_SUBJECTS_IN_PROGRESS);
    }

    @Test
    void eligibilityWithoutAnActiveEnrollmentReportsItAndNeverReadsFacts() {
        when(resolver.resolve(STUDENT_ID, null)).thenReturn(new OwnProgramEnrollmentResolver.Resolution(Optional.empty(), false));

        ProcedureEligibilityResponse response = new ApprovedSubjectsProcedureEligibility(resolver, facts, clock)
                .eligibilityOf(STUDENT_ID, null, null);

        assertThat(response.eligible()).isFalse();
        assertThat(response.reasons()).extracting(reason -> reason.code()).containsExactly(ProcedureIneligibilityCode.NO_ACTIVE_ENROLLMENT);
        verify(facts, never()).inputFor(any(), any(), any());
    }
}
