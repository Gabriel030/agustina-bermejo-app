package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.enrollment.api.EnrollmentNameFinder;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.OrganismKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionAlreadyOpenException;
import com.red_escuela.backend.graduation.certificate.regular.exception.NoDebtToExceptException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.graduation.events.CertificateExceptionRequestedEvent;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettings;
import com.red_escuela.backend.graduation.events.CertificateExceptionCreatedByDirectorEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.mockito.ArgumentCaptor;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateExceptionRequesterTest {

    private static final Long ENROLLMENT_ID = 17L;
    private static final Long STUDENT_ID = 5L;
    private static final Long REQUESTER_ID = 9L;
    private static final String REASON = "El alumno presentó un reclamo formal ante Defensa del Consumidor";

    private final CertificateExceptionApprovalRepository repository = mock(CertificateExceptionApprovalRepository.class);
    private final RegularEnrollmentFactsFinder factsFinder = mock(RegularEnrollmentFactsFinder.class);
    private final RegularStandingGate standingGate = mock(RegularStandingGate.class);
    private final CurrentActorFinder currentActorFinder = mock(CurrentActorFinder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final RegularCertificateSettingsFinder settingsFinder = mock(RegularCertificateSettingsFinder.class);
    private final AuditTrail auditTrail = mock(AuditTrail.class);
    private final RegularDebtFactsReader debtFactsReader = mock(RegularDebtFactsReader.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private final CertificateExceptionRequester requester = new CertificateExceptionRequester(repository, factsFinder, standingGate,
            currentActorFinder, eventPublisher, settingsFinder, auditTrail, clock, debtFactsReader,
            new CertificateExceptionResponses(mock(PersonNameFinder.class), mock(EnrollmentNameFinder.class)));
    private final RequestCertificateExceptionCommand command = new RequestCertificateExceptionCommand(RegularityKind.PROGRAM, ENROLLMENT_ID,
            OrganismKind.COURT, "Causa 1/2026", REASON);

    @BeforeEach
    void givenStudentWithoutOpenException() {
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(STUDENT_ID);
        when(student.getPerson()).thenReturn(mock(Person.class));
        when(factsFinder.find(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(new RegularEnrollmentFacts(student, "la carrera", "Tecnicatura"));
        when(currentActorFinder.currentPersonId()).thenReturn(REQUESTER_ID);
        when(repository.existsByProgramEnrollmentIdAndStatusIn(any(), any())).thenReturn(false);
        when(settingsFinder.current()).thenReturn(RegularCertificateSettings.DEFAULT);
        when(debtFactsReader.factsOf(any())).thenReturn(java.util.Map.of());
    }

    private DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException("conflict",
                new ConstraintViolationException("conflict", new SQLException("conflict"), constraintName));
    }

    @Test
    void concurrentOpenRequestIsReportedAsAlreadyOpenWhenTheOpenPerEnrollmentConstraintFires() {
        when(repository.saveAndFlush(any(CertificateExceptionApproval.class)))
                .thenThrow(violationOf("uq_certificate_exception_open_per_enrollment"));

        assertThatThrownBy(() -> requester.request(command)).isInstanceOf(CertificateExceptionAlreadyOpenException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void anyOtherIntegrityViolationIsRethrownUntouched() {
        DataIntegrityViolationException other = violationOf("ck_certificate_exception_reason_length");
        when(repository.saveAndFlush(any(CertificateExceptionApproval.class))).thenThrow(other);

        assertThatThrownBy(() -> requester.request(command)).isSameAs(other);
    }

    @Test
    void integrityViolationWithoutHibernateCauseIsRethrownUntouched() {
        DataIntegrityViolationException bare = new DataIntegrityViolationException("conflict");
        when(repository.saveAndFlush(any(CertificateExceptionApproval.class))).thenThrow(bare);

        assertThatThrownBy(() -> requester.request(command)).isSameAs(bare);
    }

    @Test
    void secondOpenRequestSeenBeforeSavingIsRejectedWithoutTouchingTheDatabase() {
        when(repository.existsByProgramEnrollmentIdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> requester.request(command)).isInstanceOf(CertificateExceptionAlreadyOpenException.class);

        verify(repository, never()).saveAndFlush(any(CertificateExceptionApproval.class));
    }

    @Test
    void requestWithoutDebtInFullBillingIsRefusedBeforeAnythingIsSaved() {
        doThrow(new NoDebtToExceptException()).when(standingGate).requireDebtToExcept(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID);

        assertThatThrownBy(() -> requester.request(command)).isInstanceOf(NoDebtToExceptException.class);

        verify(repository, never()).saveAndFlush(any(CertificateExceptionApproval.class));
    }

    @Test
    void savedRequestPublishesTheRequestedEventForTheDirector() {
        when(repository.saveAndFlush(any(CertificateExceptionApproval.class))).thenAnswer(invocation -> invocation.getArgument(0));

        requester.request(command);

        verify(eventPublisher).publishEvent(any(CertificateExceptionRequestedEvent.class));
    }

    @Test
    void directCreationSavesAnApprovedExceptionAuditsItAndNotifiesAdministrative() {
        when(repository.saveAndFlush(any(CertificateExceptionApproval.class))).thenAnswer(invocation -> {
            CertificateExceptionApproval saved = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 21L);
            return saved;
        });

        var response = requester.createApprovedByDirector(command);

        assertThat(response.status()).isEqualTo(com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus.APPROVED);
        assertThat(response.createdByDirector()).isTrue();
        assertThat(response.decidedById()).isEqualTo(REQUESTER_ID);
        assertThat(response.validUntil()).isEqualTo(response.decidedAt().plusDays(RegularCertificateSettings.DEFAULT.exceptionValidityDays().value()));
        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditTrail).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.CERTIFICATE_EXCEPTION_DIRECT_CREATED);
        verify(eventPublisher).publishEvent(any(CertificateExceptionCreatedByDirectorEvent.class));
        verify(eventPublisher, never()).publishEvent(any(CertificateExceptionRequestedEvent.class));
    }

    @Test
    void directCreationWithoutDebtInFullBillingIsRefusedAndLeavesNoTrace() {
        doThrow(new NoDebtToExceptException()).when(standingGate).requireDebtToExcept(STUDENT_ID, RegularityKind.PROGRAM, ENROLLMENT_ID);

        assertThatThrownBy(() -> requester.createApprovedByDirector(command)).isInstanceOf(NoDebtToExceptException.class);

        verify(repository, never()).saveAndFlush(any(CertificateExceptionApproval.class));
        verify(auditTrail, never()).record(any(AuditRecord.class));
    }

    @Test
    void directCreationRefusesASecondOpenExceptionForTheEnrollment() {
        when(repository.existsByProgramEnrollmentIdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> requester.createApprovedByDirector(command)).isInstanceOf(CertificateExceptionAlreadyOpenException.class);

        verify(auditTrail, never()).record(any(AuditRecord.class));
    }

    @Test
    void directCreationWithAShortReasonIsRefusedBeforeSaving() {
        RequestCertificateExceptionCommand shortReason = new RequestCertificateExceptionCommand(RegularityKind.PROGRAM, ENROLLMENT_ID,
                OrganismKind.COURT, "Causa 1/2026", "motivo corto");

        assertThatThrownBy(() -> requester.createApprovedByDirector(shortReason))
                .isInstanceOf(com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionReasonInvalidException.class);

        verify(repository, never()).saveAndFlush(any(CertificateExceptionApproval.class));
    }

    @Test
    void concurrentDirectCreationReportsAlreadyOpenOnTheOpenPerEnrollmentConstraint() {
        when(repository.saveAndFlush(any(CertificateExceptionApproval.class)))
                .thenThrow(violationOf("uq_certificate_exception_open_per_enrollment"));

        assertThatThrownBy(() -> requester.createApprovedByDirector(command)).isInstanceOf(CertificateExceptionAlreadyOpenException.class);

        verify(auditTrail, never()).record(any(AuditRecord.class));
    }
}
