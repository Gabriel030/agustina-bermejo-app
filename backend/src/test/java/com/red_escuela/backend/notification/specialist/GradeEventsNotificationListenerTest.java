package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.domain.DomainNotificationEvent;
import com.red_escuela.backend.evaluation.events.GradeCorrectedEvent;
import com.red_escuela.backend.evaluation.events.GradesPublishedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.NotificationEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GradeEventsNotificationListenerTest {

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final GradeEventsNotificationListener listener = new GradeEventsNotificationListener(effectGuard, publisher, entityManager);

    @BeforeEach
    void stubPersonReferences() {
        when(effectGuard.runEffectOnce(any(), anyString(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        });
        when(entityManager.getReference(eq(Person.class), anyLong())).thenAnswer(invocation -> {
            Person person = new Person();
            person.setId(invocation.getArgument(1));
            return person;
        });
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
    void publishedEventNotifiesEachStudentOnce() {
        listener.notifyGradesPublished(new GradesPublishedEvent("c", Instant.EPOCH, 7L, "Algebra - Com A", "Examen", 1, List.of(10L, 11L)));

        DomainNotificationEvent notification = published();
        assertThat(notification.type()).isEqualTo(NotificationType.GRADES_PUBLISHED);
        assertThat(recipientIds(notification)).containsExactly(10L, 11L);
        assertThat(notification.message()).isEqualTo("Se publicó tu nota de Examen 1 en Algebra - Com A");
        assertThat(notification.relatedEntityType()).isEqualTo("Section");
        assertThat(notification.relatedEntityId()).isEqualTo(7L);
    }

    @Test
    void correctedEventNotifiesOnlyTheAffectedStudent() {
        listener.notifyGradeCorrected(new GradeCorrectedEvent("c", Instant.EPOCH, 55L, 10L, "Algebra - Com A", "TP", 2));

        DomainNotificationEvent notification = published();
        assertThat(notification.type()).isEqualTo(NotificationType.GRADE_CORRECTED);
        assertThat(recipientIds(notification)).containsExactly(10L);
        assertThat(notification.message()).isEqualTo("Se corrigió tu nota de TP 2 en Algebra - Com A");
        assertThat(notification.relatedEntityId()).isEqualTo(55L);
    }

    @Test
    void replayedEventDoesNotNotifyTwice() {
        doReturn(false).when(effectGuard).runEffectOnce(any(), anyString(), any());

        listener.notifyGradesPublished(new GradesPublishedEvent("c", Instant.EPOCH, 7L, "Algebra", "Examen", 1, List.of(10L)));

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void bothGradeNotificationTypesRespectThePostedGradePreference() {
        assertThat(UserPreferenceEventResolver.resolve(NotificationType.GRADES_PUBLISHED)).contains(NotificationEvent.GRADE_POSTED);
        assertThat(UserPreferenceEventResolver.resolve(NotificationType.GRADE_CORRECTED)).contains(NotificationEvent.GRADE_POSTED);
    }
}
