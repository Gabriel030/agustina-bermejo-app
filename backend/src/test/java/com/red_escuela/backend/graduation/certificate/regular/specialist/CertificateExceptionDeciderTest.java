package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.enrollment.api.EnrollmentNameFinder;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.OrganismKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettings;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionDecisionReasonInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.OwnExceptionDecisionException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.graduation.events.CertificateExceptionDecidedEvent;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateExceptionDeciderTest {

    private static final Long EXCEPTION_ID = 21L;
    private static final Long REQUESTER_ID = 9L;
    private static final Long DIRECTOR_ID = 4L;
    private static final Long STUDENT_ID = 5L;
    private static final String REASON = "El alumno presentó un reclamo formal ante Defensa del Consumidor";
    private static final String DECISION_REASON = "Reclamo verificado con el expediente";

    private final CertificateExceptionApprovalRepository repository = mock(CertificateExceptionApprovalRepository.class);
    private final RegularCertificateSettingsFinder settingsFinder = mock(RegularCertificateSettingsFinder.class);
    private final CurrentActorFinder currentActorFinder = mock(CurrentActorFinder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private final CertificateExceptionDecider decider = new CertificateExceptionDecider(repository, settingsFinder, currentActorFinder,
            eventPublisher, clock, new CertificateExceptionResponses(mock(PersonNameFinder.class), mock(EnrollmentNameFinder.class)));

    @BeforeEach
    void givenPendingExceptionAndDirectorActing() {
        CertificateExceptionApproval approval = CertificateExceptionApproval.request(RegularityKind.PROGRAM, 17L, STUDENT_ID,
                OrganismKind.COURT, "Causa 1/2026", REASON, REQUESTER_ID, LocalDateTime.of(2026, 11, 4, 10, 0));
        when(repository.findByIdForUpdate(EXCEPTION_ID)).thenReturn(Optional.of(approval));
        when(repository.saveAndFlush(any(CertificateExceptionApproval.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(settingsFinder.current()).thenReturn(RegularCertificateSettings.DEFAULT);
        when(currentActorFinder.currentPersonId()).thenReturn(DIRECTOR_ID);
    }

    private CertificateExceptionDecidedEvent publishedEvent() {
        ArgumentCaptor<CertificateExceptionDecidedEvent> event = ArgumentCaptor.forClass(CertificateExceptionDecidedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        return event.getValue();
    }

    @Test
    void approvalPublishesTheDecidedEventForTheRequesterWithTheReason() {
        decider.approve(EXCEPTION_ID, DECISION_REASON);

        CertificateExceptionDecidedEvent event = publishedEvent();
        assertThat(event.approved()).isTrue();
        assertThat(event.requesterPersonId()).isEqualTo(REQUESTER_ID);
        assertThat(event.studentId()).isEqualTo(STUDENT_ID);
        assertThat(event.decisionReason()).isEqualTo(DECISION_REASON);
    }

    @Test
    void rejectionPublishesTheDecidedEventAsNotApproved() {
        decider.reject(EXCEPTION_ID, DECISION_REASON);

        CertificateExceptionDecidedEvent event = publishedEvent();
        assertThat(event.approved()).isFalse();
        assertThat(event.requesterPersonId()).isEqualTo(REQUESTER_ID);
        assertThat(event.decisionReason()).isEqualTo(DECISION_REASON);
    }

    @Test
    void ownDecisionPublishesNothing() {
        when(currentActorFinder.currentPersonId()).thenReturn(REQUESTER_ID);

        assertThatThrownBy(() -> decider.approve(EXCEPTION_ID, DECISION_REASON)).isInstanceOf(OwnExceptionDecisionException.class);
        assertThatThrownBy(() -> decider.reject(EXCEPTION_ID, DECISION_REASON)).isInstanceOf(OwnExceptionDecisionException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(repository, never()).saveAndFlush(any(CertificateExceptionApproval.class));
    }

    @Test
    void invalidDecisionReasonPublishesNothing() {
        assertThatThrownBy(() -> decider.approve(EXCEPTION_ID, "corto")).isInstanceOf(CertificateExceptionDecisionReasonInvalidException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void withdrawalPublishesNothing() {
        when(currentActorFinder.currentPersonId()).thenReturn(REQUESTER_ID);

        decider.withdraw(EXCEPTION_ID);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
