package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.enrollment.events.SubjectEquivalenceRecordedEvent;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceVoidedEvent;
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

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubjectEquivalenceNotificationListenerTest {

    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final SubjectEquivalenceNotificationListener listener =
            new SubjectEquivalenceNotificationListener(effectGuard, eventPublisher, entityManager);
    private final Person student = new Person();

    @BeforeEach
    void runEachEffectOnlyOnce() {
        student.setId(7L);
        when(entityManager.getReference(Person.class, 7L)).thenReturn(student);
        AtomicBoolean alreadyRun = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (alreadyRun.compareAndSet(false, true)) {
                ((Runnable) invocation.getArgument(2)).run();
                return true;
            }
            return false;
        }).when(effectGuard).runEffectOnce(any(), any(), any());
    }

    private ClosureNotification singlePublished() {
        ArgumentCaptor<ClosureNotification> captor = ArgumentCaptor.forClass(ClosureNotification.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void recordedEquivalenceNotifiesOnlyTheStudentWithTheSubjectName() {
        listener.notifyEquivalenceRecorded(SubjectEquivalenceRecordedEvent.recordedFor(11L, 5L, 3L, 7L, "Algebra"));

        ClosureNotification notification = singlePublished();
        assertThat(notification.type()).isEqualTo(NotificationType.SUBJECT_EQUIVALENCE_RECORDED);
        assertThat(notification.message())
                .isEqualTo("Te reconocieron la equivalencia de Algebra: ahora figura como aprobada en tu plan de estudio.");
        assertThat(notification.recipients()).containsExactly(new PersonRecipient(student));
        assertThat(notification.relatedEntityType()).isEqualTo("SubjectEquivalence");
        assertThat(notification.relatedEntityId()).isEqualTo(11L);
    }

    @Test
    void voidedEquivalenceNotifiesOnlyTheStudentWithoutAnyReason() {
        listener.notifyEquivalenceVoided(SubjectEquivalenceVoidedEvent.voidedFor(11L, 5L, 3L, 7L, "Algebra"));

        ClosureNotification notification = singlePublished();
        assertThat(notification.type()).isEqualTo(NotificationType.SUBJECT_EQUIVALENCE_VOIDED);
        assertThat(notification.message()).isEqualTo("Se anuló la equivalencia de Algebra. Consultá con la institución si tenés dudas.");
        assertThat(notification.message()).doesNotContain("Motivo");
        assertThat(notification.recipients()).containsExactly(new PersonRecipient(student));
    }

    @Test
    void replayedEventNotifiesExactlyOnce() {
        SubjectEquivalenceRecordedEvent event = SubjectEquivalenceRecordedEvent.recordedFor(11L, 5L, 3L, 7L, "Algebra");

        listener.notifyEquivalenceRecorded(event);
        listener.notifyEquivalenceRecorded(event);

        singlePublished();
    }

    @Test
    void missingSubjectNameFallsBackToAGenericLabel() {
        assertThat(SubjectEquivalenceNotificationListener.recordedMessage(null)).contains("equivalencia de una materia");
        assertThat(SubjectEquivalenceNotificationListener.voidedMessage("  ")).contains("equivalencia de una materia");
    }

    @Test
    void eventsAreDeduplicatedWithAHandlerNamePerType() {
        listener.notifyEquivalenceVoided(SubjectEquivalenceVoidedEvent.voidedFor(11L, 5L, 3L, 7L, "Algebra"));

        verify(effectGuard).runEffectOnce(any(), org.mockito.ArgumentMatchers.eq(SubjectEquivalenceNotificationListener.NOTIFY_VOIDED_HANDLER), any());
        verify(effectGuard, never()).runEffectOnce(any(), org.mockito.ArgumentMatchers.eq(SubjectEquivalenceNotificationListener.NOTIFY_RECORDED_HANDLER), any());
    }
}
