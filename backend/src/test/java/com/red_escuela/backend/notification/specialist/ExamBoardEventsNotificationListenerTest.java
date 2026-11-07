package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.domain.DomainNotificationEvent;
import com.red_escuela.backend.evaluation.events.ExamBoardCanceledEvent;
import com.red_escuela.backend.evaluation.events.ExamBoardRescheduledEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamBoardEventsNotificationListenerTest {

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ExamBoardEventsNotificationListener listener = new ExamBoardEventsNotificationListener(effectGuard, publisher, entityManager);

    @BeforeEach
    void stubPersonReferences() {
        when(effectGuard.runEffectOnce(any(), anyString(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        });
        when(entityManager.getReference(eq(Person.class), anyLong())).thenAnswer(invocation -> personWithId(invocation.getArgument(1)));
    }

    private static Person personWithId(Long id) {
        Person person = new Person();
        person.setId(id);
        return person;
    }

    private DomainNotificationEvent published() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        return (DomainNotificationEvent) captor.getValue();
    }

    private static List<Long> recipientIds(DomainNotificationEvent notification) {
        return notification.recipients().stream().map(recipient -> ((PersonRecipient) recipient).person().getId()).toList();
    }

    @Test
    void rescheduleNotifiesRegisteredStudentsAndTribunal() {
        listener.notifyExamBoardRescheduled(new ExamBoardRescheduledEvent("c", Instant.EPOCH, 5L, "Algebra",
                LocalDateTime.of(2026, 11, 23, 18, 0), LocalDateTime.of(2026, 12, 7, 19, 30), "Aula 7", List.of(10L, 11L), List.of(20L)));

        DomainNotificationEvent notification = published();
        assertThat(notification.type()).isEqualTo(NotificationType.EXAM_BOARD_RESCHEDULED);
        assertThat(recipientIds(notification)).containsExactly(10L, 11L, 20L);
        assertThat(notification.message()).isEqualTo("Se reprogramó la mesa de Algebra: del 23/11/2026 18:00 al 07/12/2026 19:30 · Aula 7");
        assertThat(notification.relatedEntityType()).isEqualTo("ExamBoard");
        assertThat(notification.relatedEntityId()).isEqualTo(5L);
    }

    @Test
    void cancelNotifiesRegisteredStudents() {
        listener.notifyExamBoardCanceled(new ExamBoardCanceledEvent("c", Instant.EPOCH, 5L, "Algebra",
                LocalDateTime.of(2026, 11, 23, 18, 0), List.of(10L, 11L), List.of(20L), "Falta de quórum"));

        DomainNotificationEvent notification = published();
        assertThat(notification.type()).isEqualTo(NotificationType.EXAM_BOARD_CANCELED);
        assertThat(recipientIds(notification)).containsExactly(10L, 11L, 20L);
        assertThat(notification.message()).isEqualTo("Se canceló la mesa de Algebra del 23/11/2026 18:00. Motivo: Falta de quórum");
    }
}
