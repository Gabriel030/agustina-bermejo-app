package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestMode;
import com.red_escuela.backend.billing.collections.domain.ReminderStudentFacts;
import com.red_escuela.backend.billing.collections.service.CollectionReminderFactsLoader;
import com.red_escuela.backend.billing.collections.service.StudentReminderContext;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionReminderPlanningStepTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2027, 3, 4);
    private static final LocalDate HOLIDAY = LocalDate.of(2027, 3, 8);

    private CollectionReminderFactsLoader factsLoader;
    private StudentCollectionReminderPlanner studentPlanner;
    private BillingConfigurationReader configurationReader;
    private CollectionReminderPlanningStep step;

    @BeforeEach
    void setUp() {
        factsLoader = mock(CollectionReminderFactsLoader.class);
        studentPlanner = mock(StudentCollectionReminderPlanner.class);
        configurationReader = mock(BillingConfigurationReader.class);
        BusinessDayCalendar calendar = date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY && !date.equals(HOLIDAY);
        when(configurationReader.collectionReminderSettingsEffectiveOn(any())).thenReturn(CollectionReminderSettings.initial());
        step = new CollectionReminderPlanningStep(factsLoader, studentPlanner, configurationReader, calendar);
    }

    private static StudentReminderContext contextOf(long studentId) {
        return new StudentReminderContext(new ReminderStudentFacts(studentId, List.of(), Map.of()), 100 + studentId, Map.of());
    }

    @SuppressWarnings("unchecked")
    private void everyCandidateHasFacts() {
        when(factsLoader.loadContextsOf(any(), eq(BUSINESS_DATE))).thenAnswer(invocation ->
                ((java.util.Collection<Long>) invocation.getArgument(0)).stream().map(CollectionReminderPlanningStepTest::contextOf).toList());
    }

    @Test
    @Tag("D-353")
    void aUniqueIndexViolationCountsAsAlreadyPlannedAndNotAsFailure() {
        when(factsLoader.findCandidateStudentIdsAmong(any())).thenReturn(List.of(1L));
        everyCandidateHasFacts();
        when(studentPlanner.planReminderOfStudentOn(any(), any(), any(), any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("duplicate", new ConstraintViolationException("duplicate", new SQLException("duplicate key", "23505"), "uq_collection_reminder_student_day")));

        CollectionReminderPlanningResult result = step.planCollectionRemindersOf(BUSINESS_DATE, List.of(1L));

        assertThat(result.alreadyPlanned()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @Tag("D-353")
    void anyOtherConstraintViolationCountsAsAFailureWithItsError() {
        when(factsLoader.findCandidateStudentIdsAmong(any())).thenReturn(List.of(1L));
        everyCandidateHasFacts();
        when(studentPlanner.planReminderOfStudentOn(any(), any(), any(), any(), any(), any(), any())).thenThrow(new DataIntegrityViolationException("not empty",
                new ConstraintViolationException("not empty", new SQLException("check violated", "23514"), "ck_collection_reminder_not_empty")));

        CollectionReminderPlanningResult result = step.planCollectionRemindersOf(BUSINESS_DATE, List.of(1L));

        assertThat(result.alreadyPlanned()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).containsExactly(new PlanningError(1L, "not empty"));
    }

    @Test
    @Tag("D-353")
    void aCandidateWithoutLoadedFactsIsAFailureWithItsError() {
        when(factsLoader.findCandidateStudentIdsAmong(any())).thenReturn(List.of(1L, 2L));
        when(factsLoader.loadContextsOf(any(), eq(BUSINESS_DATE))).thenReturn(List.of(contextOf(2L)));
        when(studentPlanner.planReminderOfStudentOn(any(), any(), any(), any(), any(), any(), any())).thenReturn(StudentPlanningOutcome.PLANNED);

        CollectionReminderPlanningResult result = step.planCollectionRemindersOf(BUSINESS_DATE, List.of(1L, 2L));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.planned()).isEqualTo(1);
        assertThat(result.errors()).extracting(PlanningError::studentId).containsExactly(1L);
    }

    @Test
    @Tag("D-353")
    void aStudentWithoutAResolvedScheduleFailsAloneAndTheSweepContinues() {
        when(factsLoader.findCandidateStudentIdsAmong(any())).thenReturn(List.of(1L, 2L));
        everyCandidateHasFacts();
        when(studentPlanner.planReminderOfStudentOn(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("The invoice 9 has no resolved reminder schedule"))
                .thenReturn(StudentPlanningOutcome.PLANNED);

        CollectionReminderPlanningResult result = step.planCollectionRemindersOf(BUSINESS_DATE, List.of(1L, 2L));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.planned()).isEqualTo(1);
        assertThat(result.errors()).containsExactly(new PlanningError(1L, "The invoice 9 has no resolved reminder schedule"));
    }

    @Test
    @Tag("D-353")
    void everyPageStartsAfterTheLastStudentOfThePreviousOne() {
        when(factsLoader.findCandidateStudentIdsAfter(eq(0L), anyInt())).thenReturn(List.of(4L, 7L));
        when(factsLoader.findCandidateStudentIdsAfter(eq(7L), anyInt())).thenReturn(List.of(9L));
        when(factsLoader.findCandidateStudentIdsAfter(eq(9L), anyInt())).thenReturn(List.of());
        everyCandidateHasFacts();
        when(studentPlanner.planReminderOfStudentOn(any(), any(), any(), any(), any(), any(), any())).thenReturn(StudentPlanningOutcome.NOTHING_TO_SEND);

        CollectionReminderPlanningResult result = step.planCollectionReminders(BUSINESS_DATE);

        assertThat(result.candidates()).isEqualTo(3);
        verify(factsLoader).findCandidateStudentIdsAfter(eq(9L), anyInt());
    }

    @Test
    @Tag("D-411")
    void theDailyDigestFlagOfTheSettingsOfTheDaySelectsTheDigestMode() {
        when(factsLoader.findCandidateStudentIdsAmong(any())).thenReturn(List.of(1L));
        everyCandidateHasFacts();
        when(studentPlanner.planReminderOfStudentOn(any(), any(), any(), any(), any(), any(), any())).thenReturn(StudentPlanningOutcome.PLANNED);
        CollectionReminderSettings initial = CollectionReminderSettings.initial();
        CollectionReminderSettings withoutDigest = new CollectionReminderSettings(initial.suspensionNoticeEnabled(), initial.postWithdrawalSummaryMonths(),
                initial.emailWindowStart(), initial.emailWindowEnd(), initial.dailyEmailCap(), initial.contactListDaysOverdue(), initial.contactListOverdueInstallments(), false);
        when(configurationReader.collectionReminderSettingsEffectiveOn(BUSINESS_DATE)).thenReturn(withoutDigest);

        step.planCollectionRemindersOf(BUSINESS_DATE, List.of(1L));
        when(configurationReader.collectionReminderSettingsEffectiveOn(BUSINESS_DATE)).thenReturn(initial);
        step.planCollectionRemindersOf(BUSINESS_DATE, List.of(1L));

        ArgumentCaptor<ReminderDigestMode> modes = ArgumentCaptor.forClass(ReminderDigestMode.class);
        verify(studentPlanner, org.mockito.Mockito.times(2)).planReminderOfStudentOn(any(), any(), any(), any(), modes.capture(), any(), any());
        assertThat(modes.getAllValues()).containsExactly(ReminderDigestMode.SEPARATE_MESSAGES, ReminderDigestMode.DAILY_DIGEST);
    }

    @Test
    @Tag("D-342")
    void theCalendarOfTheRunCarriesWeekendsAndHolidaysAroundTheBusinessDate() {
        when(factsLoader.findCandidateStudentIdsAmong(any())).thenReturn(List.of(1L));
        everyCandidateHasFacts();
        when(studentPlanner.planReminderOfStudentOn(any(), any(), any(), any(), any(), any(), any())).thenReturn(StudentPlanningOutcome.PLANNED);

        step.planCollectionRemindersOf(BUSINESS_DATE, List.of(1L));

        ArgumentCaptor<NonBusinessDays> calendars = ArgumentCaptor.forClass(NonBusinessDays.class);
        verify(studentPlanner).planReminderOfStudentOn(any(), any(), any(), calendars.capture(), any(), any(), any());
        NonBusinessDays calendar = calendars.getValue();
        assertThat(calendar.isBusinessDay(HOLIDAY)).isFalse();
        assertThat(calendar.isBusinessDay(LocalDate.of(2027, 3, 6))).isFalse();
        assertThat(calendar.isBusinessDay(LocalDate.of(2027, 3, 9))).isTrue();
        assertThat(calendar.isBusinessDay(BUSINESS_DATE.minusDays(14))).isTrue();
    }
}
