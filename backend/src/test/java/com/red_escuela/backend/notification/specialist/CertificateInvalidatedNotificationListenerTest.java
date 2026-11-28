package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.graduation.events.CertificateInvalidatedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.service.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateInvalidatedNotificationListenerTest {

    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final CertificateInvalidatedNotificationListener listener =
            new CertificateInvalidatedNotificationListener(effectGuard, eventPublisher, entityManager);
    private final Person person = new Person();

    @BeforeEach
    void stubGuardAndPerson() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return true;
        }).when(effectGuard).runEffectOnce(any(), any(), any());
        person.setId(7L);
        when(entityManager.getReference(Person.class, 7L)).thenReturn(person);
    }

    private ClosureNotification notify(String kind) {
        listener.notifyCertificateInvalidated(CertificateInvalidatedEvent.of(11L, kind, 7L, "La matrícula de la carrera fue dada de baja"));
        ArgumentCaptor<ClosureNotification> published = ArgumentCaptor.forClass(ClosureNotification.class);
        verify(eventPublisher).publishEvent(published.capture());
        return published.getValue();
    }

    @Test
    void regularCertificateNoticeExplainsTheReasonAndHowToReissue() {
        ClosureNotification notification = notify(CertificateInvalidatedEvent.REGULAR);

        assertThat(notification.type()).isEqualTo(NotificationType.CERTIFICATE_INVALIDATED);
        assertThat(notification.message()).contains("alumno regular").contains("La matrícula de la carrera fue dada de baja")
                .contains("pedí una nueva").contains("límite diario");
        assertThat(notification.recipients()).containsExactly(new PersonRecipient(person));
        assertThat(notification.relatedEntityId()).isEqualTo(11L);
        assertThat(notification.relatedEntityType()).isEqualTo("RegularStudentCertificate");
    }

    @Test
    void approvedSubjectsNoticeNamesTheCertificateType() {
        ClosureNotification notification = notify(CertificateInvalidatedEvent.APPROVED_SUBJECTS);

        assertThat(notification.message()).contains("materias aprobadas");
        assertThat(notification.relatedEntityType()).isEqualTo("ApprovedSubjectsCertificate");
    }
}
