package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.events.StudentReactivatedEvent;
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

class StudentReactivationNotificationListenerTest {

    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final StudentReactivationNotificationListener listener =
            new StudentReactivationNotificationListener(effectGuard, eventPublisher, entityManager);

    @Test
    void reactivatedStudentIsNotifiedInSpanishWithTheirOwnAccount() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return true;
        }).when(effectGuard).runEffectOnce(any(), any(), any());
        Person person = new Person();
        person.setId(7L);
        when(entityManager.getReference(Person.class, 7L)).thenReturn(person);

        listener.notifyStudentReactivated(StudentReactivatedEvent.of(3L, 7L));

        ArgumentCaptor<ClosureNotification> published = ArgumentCaptor.forClass(ClosureNotification.class);
        verify(eventPublisher).publishEvent(published.capture());
        ClosureNotification notification = published.getValue();
        assertThat(notification.type()).isEqualTo(NotificationType.STUDENT_REACTIVATED);
        assertThat(notification.message()).contains("reactivada");
        assertThat(notification.recipients()).containsExactly(new PersonRecipient(person));
        assertThat(notification.relatedEntityId()).isEqualTo(3L);
    }
}
