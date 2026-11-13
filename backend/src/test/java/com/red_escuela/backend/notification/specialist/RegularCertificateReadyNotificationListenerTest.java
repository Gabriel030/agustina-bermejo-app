package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.graduation.events.RegularCertificateRequestFulfilledEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegularCertificateReadyNotificationListenerTest {

    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final RegularCertificateReadyNotificationListener listener =
            new RegularCertificateReadyNotificationListener(effectGuard, eventPublisher, entityManager);

    @Test
    void studentIsToldTheirCertificateIsReadyThroughTheirOwnAccount() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return true;
        }).when(effectGuard).runEffectOnce(any(), any(), any());
        Person person = new Person();
        person.setId(7L);
        when(entityManager.getReference(Person.class, 7L)).thenReturn(person);

        listener.notifyCertificateReady(RegularCertificateRequestFulfilledEvent.of(11L, 3L, 7L));

        ArgumentCaptor<ClosureNotification> published = ArgumentCaptor.forClass(ClosureNotification.class);
        verify(eventPublisher).publishEvent(published.capture());
        ClosureNotification notification = published.getValue();
        assertThat(notification.type()).isEqualTo(NotificationType.REGULAR_CERTIFICATE_READY);
        assertThat(notification.message()).contains("constancia");
        assertThat(notification.recipients()).containsExactly(new PersonRecipient(person));
        assertThat(notification.relatedEntityId()).isEqualTo(11L);
    }
}
