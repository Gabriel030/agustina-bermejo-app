package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.enrollment.events.SubjectEnrollmentWithdrawnEvent;
import com.red_escuela.backend.enrollment.events.SubjectSectionChangedEvent;
import com.red_escuela.backend.enrollment.events.WaitlistEntryPausedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.ClosureNotification;
import com.red_escuela.backend.notification.domain.PersonRecipient;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrollmentChangeNotificationListenerTest {

    private final ProcessedEffectGuard effectGuard = mock(ProcessedEffectGuard.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final EnrollmentChangeNotificationListener listener = new EnrollmentChangeNotificationListener(effectGuard, eventPublisher, entityManager);

    private void guardRunsEachEffectOnlyOnce() {
        AtomicBoolean alreadyRun = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (alreadyRun.compareAndSet(false, true)) {
                ((Runnable) invocation.getArgument(2)).run();
                return true;
            }
            return false;
        }).when(effectGuard).runEffectOnce(any(), any(), any());
    }

    private void personsExist(Long... ids) {
        for (Long id : ids) {
            Person person = new Person();
            person.setId(id);
            when(entityManager.getReference(Person.class, id)).thenReturn(person);
        }
    }

    private List<ClosureNotification> published(int times) {
        ArgumentCaptor<ClosureNotification> captor = ArgumentCaptor.forClass(ClosureNotification.class);
        verify(eventPublisher, times(times)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void withdrawalByStaffNotifiesTheStudentAndEachTeacherExactlyOnceEvenIfTheEventIsReplayed() {
        guardRunsEachEffectOnlyOnce();
        personsExist(7L, 20L, 21L);
        SubjectEnrollmentWithdrawnEvent event = SubjectEnrollmentWithdrawnEvent.of(5L, 7L, "Ana Perez", "Algebra", "Com A",
                List.of(20L, 21L, 20L), true, "Pidió la baja");

        listener.notifySubjectWithdrawn(event);
        listener.notifySubjectWithdrawn(event);

        List<ClosureNotification> notifications = published(2);
        assertThat(notifications.get(0).type()).isEqualTo(NotificationType.SUBJECT_WITHDRAWN);
        assertThat(notifications.get(0).message()).contains("Algebra", "Com A", "Pidió la baja");
        assertThat(notifications.get(0).recipients()).containsExactly(new PersonRecipient(entityManager.getReference(Person.class, 7L)));
        assertThat(notifications.get(1).type()).isEqualTo(NotificationType.SUBJECT_WITHDRAWN_TEACHER);
        assertThat(notifications.get(1).message()).contains("Ana Perez");
        assertThat(notifications.get(1).recipients()).hasSize(2);
        assertThat(notifications.get(1).relatedEntityId()).isEqualTo(5L);
    }

    @Test
    void withdrawalMadeByTheStudentOnlyNotifiesTeachers() {
        guardRunsEachEffectOnlyOnce();
        personsExist(7L, 20L);
        listener.notifySubjectWithdrawn(SubjectEnrollmentWithdrawnEvent.of(5L, 7L, "Ana Perez", "Algebra", "Com A", List.of(20L), false, null));

        assertThat(published(1).get(0).type()).isEqualTo(NotificationType.SUBJECT_WITHDRAWN_TEACHER);
    }

    @Test
    void withdrawalWithoutTeachersAndWithoutStudentNoticePublishesNothing() {
        guardRunsEachEffectOnlyOnce();
        listener.notifySubjectWithdrawn(SubjectEnrollmentWithdrawnEvent.of(5L, 7L, "Ana Perez", "Algebra", "Com A", List.of(), false, null));

        verify(eventPublisher, never()).publishEvent(any(ClosureNotification.class));
    }

    @Test
    void sectionChangeNotifiesStudentAndTheTeachersOfBothSectionsWithDifferentMessages() {
        guardRunsEachEffectOnlyOnce();
        personsExist(7L, 20L, 30L);
        SubjectSectionChangedEvent event = SubjectSectionChangedEvent.of(9L, 7L, "Ana Perez", "Algebra", "Com A", "Com B",
                List.of(20L), List.of(30L), true, "Cambio por trabajo");

        listener.notifySectionChanged(event);
        listener.notifySectionChanged(event);

        List<ClosureNotification> notifications = published(3);
        assertThat(notifications).extracting(ClosureNotification::type)
                .containsExactly(NotificationType.SECTION_CHANGED, NotificationType.SECTION_CHANGED_TEACHER, NotificationType.SECTION_CHANGED_TEACHER);
        assertThat(notifications.get(0).message()).contains("Com A", "Com B", "Cambio por trabajo");
        assertThat(notifications.get(1).message()).contains("dejó", "Com A");
        assertThat(notifications.get(2).message()).contains("se sumó", "Com B");
    }

    @Test
    void waitlistPromotionNotifiesTheStudentOnceEvenIfTheEventIsReplayed() {
        guardRunsEachEffectOnlyOnce();
        personsExist(7L);
        com.red_escuela.backend.enrollment.events.SubjectWaitlistPromotedEvent event =
                com.red_escuela.backend.enrollment.events.SubjectWaitlistPromotedEvent.of(5L, 7L, "Algebra", "Com A");

        listener.notifyWaitlistPromoted(event);
        listener.notifyWaitlistPromoted(event);

        List<ClosureNotification> notifications = published(1);
        assertThat(notifications.get(0).type()).isEqualTo(NotificationType.WAITLIST_PROMOTED);
        assertThat(notifications.get(0).message()).contains("lista de espera", "Algebra", "Com A");
    }

    @Test
    void waitlistRemovalNotifiesWithItsOwnTypeAndNeverExposesAnInternalReason() {
        guardRunsEachEffectOnlyOnce();
        personsExist(7L);

        listener.notifyWaitlistEntryRemoved(com.red_escuela.backend.enrollment.events.WaitlistEntryRemovedEvent.of(5L, 7L, "Algebra"));

        ClosureNotification notification = published(1).get(0);
        assertThat(notification.type()).isEqualTo(NotificationType.WAITLIST_REMOVED_BY_STAFF);
        assertThat(notification.message()).isEqualTo("Te quitamos de la lista de espera de Algebra. Consultá con la institución si tenés dudas.");
        assertThat(notification.message()).doesNotContain("Motivo");
    }

    @Test
    void waitlistExpiryNotifiesWithItsOwnType() {
        guardRunsEachEffectOnlyOnce();
        personsExist(7L);

        listener.notifyWaitlistEntryExpired(com.red_escuela.backend.enrollment.events.WaitlistEntryExpiredEvent.of(5L, 7L, "Algebra"));

        ClosureNotification notification = published(1).get(0);
        assertThat(notification.type()).isEqualTo(NotificationType.WAITLIST_ENTRY_EXPIRED);
        assertThat(notification.message()).isEqualTo("Venció tu lugar en la lista de espera de Algebra porque cerró la inscripción.");
    }

    @Test
    void waitlistExpiryReminderNotifiesWithItsOwnType() {
        guardRunsEachEffectOnlyOnce();
        personsExist(7L);

        listener.notifyWaitlistExpiryReminder(com.red_escuela.backend.enrollment.events.WaitlistExpiryReminderEvent.of(5L, 7L, "Algebra",
                java.time.LocalDate.of(2047, 3, 1)));

        ClosureNotification notification = published(1).get(0);
        assertThat(notification.type()).isEqualTo(NotificationType.WAITLIST_EXPIRING_SOON);
        assertThat(notification.message()).isEqualTo("Tu lugar en la lista de espera de Algebra vence el 2047-03-01 si no se libera un cupo.");
    }

    @Test
    void pausedEntryNotifiesTheStudentWithTheConflictingSectionName() {
        guardRunsEachEffectOnlyOnce();
        personsExist(7L);

        listener.notifyWaitlistEntryPaused(WaitlistEntryPausedEvent.of(5L, 7L, "Algebra", "Historia II"));

        ClosureNotification notification = published(1).get(0);
        assertThat(notification.type()).isEqualTo(NotificationType.WAITLIST_PAUSED_BY_SCHEDULE_OVERLAP);
        assertThat(notification.message()).isEqualTo("Tu lugar en la lista de espera de Algebra está en pausa porque se superpone con Historia II. "
                + "Conservás tu posición: cuando se resuelva el conflicto vas a poder ser promovido.");
    }

    @Test
    void pausedEntryDeduplicationIdentityDependsOnlyOnTheEnrollmentAndTheConflictingSection() {
        ArgumentCaptor<DeduplicableDomainEvent> captor = ArgumentCaptor.forClass(DeduplicableDomainEvent.class);

        listener.notifyWaitlistEntryPaused(WaitlistEntryPausedEvent.of(5L, 7L, "Algebra", "Historia II"));
        listener.notifyWaitlistEntryPaused(WaitlistEntryPausedEvent.of(5L, 7L, "Algebra", "Historia II"));
        listener.notifyWaitlistEntryPaused(WaitlistEntryPausedEvent.of(5L, 7L, "Algebra", "Fisica"));
        listener.notifyWaitlistEntryPaused(WaitlistEntryPausedEvent.of(6L, 8L, "Algebra", "Historia II"));

        verify(effectGuard, times(4)).runEffectOnce(captor.capture(), eq(EnrollmentChangeNotificationListener.NOTIFY_ENTRY_PAUSED_HANDLER), any());
        List<DeduplicableDomainEvent> identities = captor.getAllValues();
        assertThat(identities.get(0).eventId()).isEqualTo(identities.get(1).eventId());
        assertThat(identities.get(0).eventId()).isNotEqualTo(identities.get(2).eventId());
        assertThat(identities.get(0).eventId()).isNotEqualTo(identities.get(3).eventId());
    }
}
