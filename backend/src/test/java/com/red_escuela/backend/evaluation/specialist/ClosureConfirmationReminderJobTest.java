package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.api.SectionTermEndFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.api.ClosureNotificationTargetsFinder;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.events.ClosureConfirmationReminderEvent;
import com.red_escuela.backend.evaluation.repository.CommissionClosureReminderRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.PublishedSectionRef;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClosureConfirmationReminderJobTest {

    private static final LocalDate TERM_END = LocalDate.of(2026, 12, 10);

    private final SectionGradeItemPublicationRepository publicationRepository = mock(SectionGradeItemPublicationRepository.class);
    private final CommissionClosureRepository closureRepository = mock(CommissionClosureRepository.class);
    private final CommissionClosureReminderRepository reminderRepository = mock(CommissionClosureReminderRepository.class);
    private final SectionTermEndFinder sectionTermEndFinder = mock(SectionTermEndFinder.class);
    private final ClosureNotificationTargetsFinder targetsFinder = mock(ClosureNotificationTargetsFinder.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private ClosureConfirmationReminderJob jobOn(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        when(publicationRepository.findDistinctPublishedSections()).thenReturn(List.of(new PublishedSectionRef(SectionKind.PROGRAM, 9L)));
        when(sectionTermEndFinder.termEndDate(SectionKind.PROGRAM, 9L)).thenReturn(TERM_END);
        when(targetsFinder.findInstructorPersonIds(SectionKind.PROGRAM, 9L)).thenReturn(List.of(77L));
        when(targetsFinder.findSectionLabel(SectionKind.PROGRAM, 9L)).thenReturn("Teorías · A");
        when(reminderRepository.insertIfAbsent(any(), any(), anyInt(), any())).thenReturn(1);
        return new ClosureConfirmationReminderJob(publicationRepository, closureRepository, reminderRepository, sectionTermEndFinder,
                targetsFinder, eventPublisher, clock);
    }

    private ClosureConfirmationReminderJob jobOnDaysAfterTermEnd(int days) {
        return jobOn(TERM_END.plusDays(days));
    }

    @Test
    void nothingIsSentBeforeThreeDaysHavePassed() {
        assertThat(jobOnDaysAfterTermEnd(2).remindDueSections()).isZero();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void theFirstReminderGoesOutOnTheThirdDayToTheInstructorsWithTheSectionLabel() {
        assertThat(jobOnDaysAfterTermEnd(3).remindDueSections()).isEqualTo(1);

        ArgumentCaptor<ClosureConfirmationReminderEvent> event = ArgumentCaptor.forClass(ClosureConfirmationReminderEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().daysAfterTermEnd()).isEqualTo(3);
        assertThat(event.getValue().instructorPersonIds()).containsExactly(77L);
        assertThat(event.getValue().sectionLabel()).isEqualTo("Teorías · A");
        verify(reminderRepository).insertIfAbsent(eq(SectionKind.PROGRAM), eq(9L), eq(3), any());
    }

    @Test
    void theSecondReminderGoesOutOnTheSeventhDayAndStillOnTheFourteenth() {
        jobOnDaysAfterTermEnd(7).remindDueSections();
        verify(reminderRepository).insertIfAbsent(eq(SectionKind.PROGRAM), eq(9L), eq(7), any());

        jobOnDaysAfterTermEnd(14).remindDueSections();
        verify(reminderRepository, org.mockito.Mockito.times(2)).insertIfAbsent(eq(SectionKind.PROGRAM), eq(9L), eq(7), any());
    }

    @Test
    void nothingIsSentMoreThanTwoWeeksAfterTheTermEnd() {
        assertThat(jobOnDaysAfterTermEnd(15).remindDueSections()).isZero();

        verify(reminderRepository, never()).insertIfAbsent(any(), any(), anyInt(), any());
    }

    @Test
    void aReminderAlreadyRecordedIsNotPublishedAgain() {
        ClosureConfirmationReminderJob job = jobOnDaysAfterTermEnd(3);
        when(reminderRepository.insertIfAbsent(any(), any(), anyInt(), any())).thenReturn(0);

        assertThat(job.remindDueSections()).isZero();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aSectionWhoseTeacherAlreadyConfirmedOrWhoseActaIsClosedIsNotReminded() {
        ClosureConfirmationReminderJob job = jobOnDaysAfterTermEnd(7);
        CommissionClosure confirmed = CommissionClosure.builder().status(ClosureStatus.TEACHER_CONFIRMED).build();
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 9L)).thenReturn(Optional.of(confirmed));

        assertThat(job.remindDueSections()).isZero();

        CommissionClosure closed = CommissionClosure.builder().status(ClosureStatus.CLOSED).build();
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 9L)).thenReturn(Optional.of(closed));
        assertThat(job.remindDueSections()).isZero();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aSectionWithoutInstructorsIsSkippedWithoutRecordingTheReminder() {
        ClosureConfirmationReminderJob job = jobOnDaysAfterTermEnd(3);
        when(targetsFinder.findInstructorPersonIds(SectionKind.PROGRAM, 9L)).thenReturn(List.of());

        assertThat(job.remindDueSections()).isZero();

        verify(reminderRepository, never()).insertIfAbsent(any(), any(), anyInt(), any());
    }

    @Test
    void aStillOpenDraftIsReminded() {
        ClosureConfirmationReminderJob job = jobOnDaysAfterTermEnd(3);
        CommissionClosure draft = CommissionClosure.builder().status(ClosureStatus.DRAFT_AVAILABLE).build();
        when(closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, 9L)).thenReturn(Optional.of(draft));

        assertThat(job.remindDueSections()).isEqualTo(1);
    }
}
