package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.graduation.events.CertificateExceptionCreatedByDirectorEvent;
import com.red_escuela.backend.graduation.events.CertificateExceptionDecidedEvent;
import com.red_escuela.backend.graduation.events.CertificateExceptionRequestedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.NotificationTopic;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.domain.RoleRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
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

class CertificateExceptionNotificationListenerTest {

    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final CertificateExceptionNotificationListener listener =
            new CertificateExceptionNotificationListener(effectGuard, eventPublisher, entityManager);

    @BeforeEach
    void runGuardedEffectsInline() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return true;
        }).when(effectGuard).runEffectOnce(any(), any(), any());
    }

    @Test
    void directorsAreToldAnExceptionIsWaitingForTheirDecision() {
        listener.notifyExceptionRequested(CertificateExceptionRequestedEvent.of(21L, 3L, 7L));

        ClosureNotification notification = publishedNotification();
        assertThat(notification.type()).isEqualTo(NotificationType.CERTIFICATE_EXCEPTION_REQUESTED);
        assertThat(notification.recipients()).containsExactly(NotificationTopic.DIRECTOR_APPROVALS);
        assertThat(notification.relatedEntityId()).isEqualTo(21L);
    }

    @Test
    void requesterIsToldTheExceptionWasApprovedWithTheDirectorReason() {
        Person requester = requesterPerson();

        listener.notifyExceptionDecided(CertificateExceptionDecidedEvent.of(21L, 3L, 7L, true, "Reclamo verificado"));

        ClosureNotification notification = publishedNotification();
        assertThat(notification.type()).isEqualTo(NotificationType.CERTIFICATE_EXCEPTION_DECIDED);
        assertThat(notification.message()).contains("aprobado").contains("Reclamo verificado");
        assertThat(notification.recipients()).containsExactly(new PersonRecipient(requester));
    }

    @Test
    void requesterIsToldTheExceptionWasRejected() {
        requesterPerson();

        listener.notifyExceptionDecided(CertificateExceptionDecidedEvent.of(21L, 3L, 7L, false, "Sin respaldo suficiente"));

        assertThat(publishedNotification().message()).contains("rechazado").contains("Sin respaldo suficiente");
    }

    @Test
    void administrativeStaffIsToldTheDirectorCreatedAnApprovedException() {
        listener.notifyExceptionCreatedByDirector(CertificateExceptionCreatedByDirectorEvent.of(21L, 3L, 4L));

        ClosureNotification notification = publishedNotification();
        assertThat(notification.type()).isEqualTo(NotificationType.CERTIFICATE_EXCEPTION_DIRECT_CREATED);
        assertThat(notification.recipients()).containsExactly(new RoleRecipient("ADMINISTRATIVO"));
        assertThat(notification.relatedEntityId()).isEqualTo(21L);
    }

    private Person requesterPerson() {
        Person person = new Person();
        person.setId(7L);
        when(entityManager.getReference(Person.class, 7L)).thenReturn(person);
        return person;
    }

    private ClosureNotification publishedNotification() {
        ArgumentCaptor<ClosureNotification> published = ArgumentCaptor.forClass(ClosureNotification.class);
        verify(eventPublisher).publishEvent(published.capture());
        return published.getValue();
    }
}
