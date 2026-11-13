package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.evaluation.api.ClosureNotificationTargets;
import com.red_escuela.backend.evaluation.api.ClosureNotificationTargetsFinder;
import com.red_escuela.backend.evaluation.api.ClosureStudentNotificationTarget;
import com.red_escuela.backend.evaluation.events.ClosureApprovedEvent;
import com.red_escuela.backend.evaluation.events.ClosureConfirmationReminderEvent;
import com.red_escuela.backend.evaluation.events.ClosureExceptionGrantedEvent;
import com.red_escuela.backend.evaluation.events.ClosureReopenedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.NotificationTopic;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClosureEventsNotificationListenerTest {

    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ClosureNotificationTargetsFinder targetsFinder = mock(ClosureNotificationTargetsFinder.class);
    private final ClosureEventsNotificationListener listener =
            new ClosureEventsNotificationListener(effectGuard, eventPublisher, entityManager, targetsFinder);

    @BeforeEach
    void runGuardedEffects() {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return true;
        }).when(effectGuard).runEffectOnce(any(), any(), any());
        when(entityManager.getReference(Person.class, 11L)).thenReturn(person(11L));
        when(entityManager.getReference(Person.class, 12L)).thenReturn(person(12L));
        when(entityManager.getReference(Person.class, 77L)).thenReturn(person(77L));
        when(entityManager.getReference(Person.class, 78L)).thenReturn(person(78L));
        when(entityManager.getReference(Person.class, 88L)).thenReturn(person(88L));
        when(targetsFinder.findClosureTargets(6L)).thenReturn(new ClosureNotificationTargets("Historia · B", "ACTA-2026-0043", 1, List.of(88L),
                List.of(new ClosureStudentNotificationTarget(13L, "Promociona")), 1, 0));
        when(targetsFinder.findClosureTargets(7L)).thenReturn(new ClosureNotificationTargets("Teorías · C", "ACTA-2026-0044", 1, List.of(77L, 78L),
                List.of(new ClosureStudentNotificationTarget(11L, "Promociona")), 1, 0));
        when(targetsFinder.findClosureTargets(5L)).thenReturn(new ClosureNotificationTargets("Teorías · A", "ACTA-2026-0042", 1, List.of(77L),
                List.of(new ClosureStudentNotificationTarget(11L, "Promociona"), new ClosureStudentNotificationTarget(12L, "Va a final")), 1, 1));
    }

    private Person person(Long id) {
        Person person = new Person();
        person.setId(id);
        return person;
    }

    private List<ClosureNotification> published(int expected) {
        ArgumentCaptor<ClosureNotification> captor = ArgumentCaptor.forClass(ClosureNotification.class);
        verify(eventPublisher, times(expected)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void approvingTheActaNotifiesEachStudentOnlyWithTheirOwnResultAndTheTeachersWithTheSummary() {
        listener.notifyClosureApproved(ClosureApprovedEvent.of(5L, "PROGRAM", 9L, "ACTA-2026-0042", 1, false));

        List<ClosureNotification> notifications = published(3);
        assertThat(notifications).extracting(ClosureNotification::type)
                .containsExactly(NotificationType.CLOSURE_APPROVED, NotificationType.CLOSURE_APPROVED, NotificationType.CLOSURE_APPROVED_SUMMARY);
        assertThat(notifications.get(0).message()).contains("«Promociona»").doesNotContain("Va a final");
        assertThat(notifications.get(1).message()).contains("«Va a final»");
        assertThat(notifications.get(0).recipients()).hasSize(1);
        assertThat(notifications.get(2).message()).contains("ACTA-2026-0042", "1 promocionan", "1 van a final");
        assertThat(notifications.get(2).recipients()).extracting(recipient -> ((PersonRecipient) recipient).person().getId()).containsExactly(77L);
    }

    @Test
    void reopeningTellsTheTeacherTheReasonAndTheStudentsOnlyThatTheResultMayChange() {
        listener.notifyClosureReopened(ClosureReopenedEvent.of(5L, "PROGRAM", 9L, "ACTA-2026-0042", 2, "Error en la nota del parcial"));

        List<ClosureNotification> notifications = published(2);
        assertThat(notifications.get(0).message()).contains("Error en la nota del parcial", "volver a confirmar");
        assertThat(notifications.get(1).message()).doesNotContain("Error en la nota del parcial").contains("puede cambiar");
        assertThat(notifications.get(1).recipients()).hasSize(2);
        assertThat(notifications).extracting(ClosureNotification::type).containsOnly(NotificationType.CLOSURE_REOPENED);
    }

    @Test
    void anExceptionIsAnnouncedToTheSecretariatTopicWithoutNamingTheStudent() {
        listener.notifyClosureExceptionGranted(ClosureExceptionGrantedEvent.of(5L, "PROGRAM", 9L, 31L, "Legajo en trámite"));

        ClosureNotification notification = published(2).get(0);
        assertThat(notification.type()).isEqualTo(NotificationType.CLOSURE_EXCEPTION_GRANTED);
        assertThat(notification.recipients()).containsExactly(NotificationTopic.CLOSURE_SECRETARIAT);
        assertThat(notification.message()).contains("Teorías · A", "Legajo en trámite");
    }

    @Test
    void theReminderGoesToTheInstructorsAndNamesTheDaysElapsed() {
        listener.notifyClosureConfirmationReminder(ClosureConfirmationReminderEvent.of("PROGRAM", 9L, "Teorías · A", List.of(77L), 7));

        ClosureNotification notification = published(1).get(0);
        assertThat(notification.type()).isEqualTo(NotificationType.CLOSURE_CONFIRMATION_REMINDER);
        assertThat(notification.message()).contains("7 días", "Teorías · A");
        assertThat(notification.relatedEntityType()).isEqualTo("SubjectSection");
        assertThat(notification.relatedEntityId()).isEqualTo(9L);
    }

    private List<Long> recipientPersonIds(ClosureNotification notification) {
        return notification.recipients().stream().map(recipient -> ((PersonRecipient) recipient).person().getId()).toList();
    }

    @Test
    void anExceptionAlsoNotifiesTheTeacherOfTheCommissionWithoutReasonNorStudentData() {
        listener.notifyClosureExceptionGranted(ClosureExceptionGrantedEvent.of(5L, "PROGRAM", 9L, 31L, "Legajo en trámite"));

        ClosureNotification teacherNotice = published(2).get(1);
        assertThat(teacherNotice.type()).isEqualTo(NotificationType.CLOSURE_EXCEPTION_GRANTED_TEACHER);
        assertThat(recipientPersonIds(teacherNotice)).containsExactly(77L);
        assertThat(teacherNotice.message()).contains("Teorías · A", "planilla").doesNotContain("Legajo en trámite", "pago", "documento");
    }

    @Test
    void theTeacherNoticeNeverReachesTheStudentsNorTeachersOfOtherCommissions() {
        listener.notifyClosureExceptionGranted(ClosureExceptionGrantedEvent.of(6L, "PROGRAM", 10L, 32L, "Beca"));

        ClosureNotification teacherNotice = published(2).get(1);
        assertThat(recipientPersonIds(teacherNotice)).containsExactly(88L).doesNotContain(77L, 13L, 11L, 12L);
    }

    @Test
    void everyTeacherOfTheCommissionIsNotifiedInASingleNotification() {
        listener.notifyClosureExceptionGranted(ClosureExceptionGrantedEvent.of(7L, "PROGRAM", 11L, 33L, "Beca"));

        ClosureNotification teacherNotice = published(2).get(1);
        assertThat(recipientPersonIds(teacherNotice)).containsExactly(77L, 78L);
    }

    @Test
    void theTeacherNoticeIsSentThroughTheOnceOnlyGuard() {
        ClosureExceptionGrantedEvent event = ClosureExceptionGrantedEvent.of(5L, "PROGRAM", 9L, 31L, "Legajo en trámite");
        doAnswer(invocation -> false).when(effectGuard).runEffectOnce(any(), any(), any());

        listener.notifyClosureExceptionGranted(event);

        verify(eventPublisher, times(0)).publishEvent(any(ClosureNotification.class));
        verify(effectGuard).runEffectOnce(any(), org.mockito.ArgumentMatchers.eq(ClosureEventsNotificationListener.NOTIFY_CLOSURE_EXCEPTION_GRANTED_HANDLER), any());
    }
}
