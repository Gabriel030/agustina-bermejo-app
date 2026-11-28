package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.graduation.events.GraduationCertificateIssuedEvent;
import com.red_escuela.backend.graduation.events.GraduationCertificateReissuedEvent;
import com.red_escuela.backend.graduation.events.GraduationCertificateRevokedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.GraduationCertificateNotification;
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

class GraduationCertificateNotificationListenerTest {

    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final GraduationCertificateNotificationListener listener =
            new GraduationCertificateNotificationListener(effectGuard, eventPublisher, entityManager);
    private final Person person = new Person();

    @BeforeEach
    void runEffectsInline() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return true;
        }).when(effectGuard).runEffectOnce(any(), any(), any());
        person.setId(7L);
        when(entityManager.getReference(Person.class, 7L)).thenReturn(person);
    }

    private GraduationCertificateNotification published() {
        ArgumentCaptor<GraduationCertificateNotification> captor = ArgumentCaptor.forClass(GraduationCertificateNotification.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void issuedCertificateNotifiesTheHolderInTheApp() {
        listener.notifyIssued(GraduationCertificateIssuedEvent.of(11L, 7L, "Tecnicatura"));

        GraduationCertificateNotification notification = published();
        assertThat(notification.type()).isEqualTo(NotificationType.GRADUATION_CERTIFICATE_ISSUED);
        assertThat(notification.recipients()).containsExactly(new PersonRecipient(person));
        assertThat(notification.relatedEntityId()).isEqualTo(11L);
        assertThat(notification.message()).contains("Tecnicatura");
    }

    @Test
    void revokedCertificateNotifiesTheHolderWithoutAnyReason() {
        listener.notifyRevoked(GraduationCertificateRevokedEvent.of(11L, 7L, "Tecnicatura"));

        GraduationCertificateNotification notification = published();
        assertThat(notification.type()).isEqualTo(NotificationType.GRADUATION_CERTIFICATE_REVOKED);
        assertThat(notification.recipients()).containsExactly(new PersonRecipient(person));
    }

    @Test
    void reissuedCertificateNotifiesTheHolderAboutTheReplacement() {
        listener.notifyReissued(GraduationCertificateReissuedEvent.of(12L, 11L, 7L, "Tecnicatura"));

        GraduationCertificateNotification notification = published();
        assertThat(notification.type()).isEqualTo(NotificationType.GRADUATION_CERTIFICATE_REISSUED);
        assertThat(notification.relatedEntityId()).isEqualTo(12L);
    }
}
