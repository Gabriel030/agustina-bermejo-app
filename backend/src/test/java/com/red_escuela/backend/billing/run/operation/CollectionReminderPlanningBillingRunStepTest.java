package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.domain.MissedReminderRecovery;
import com.red_escuela.backend.billing.collections.operation.CollectionReminderPlanningResult;
import com.red_escuela.backend.billing.collections.operation.CollectionReminderPlanningStep;
import com.red_escuela.backend.billing.collections.operation.MissedReminderDaysFinder;
import com.red_escuela.backend.billing.collections.operation.OverdueContactListPublicationStep;
import com.red_escuela.backend.billing.collections.operation.PlanningError;
import com.red_escuela.backend.billing.collections.operation.PostWithdrawalSummaryPlanningStep;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRunStore;
import com.red_escuela.backend.billing.run.domain.BillingRunItemStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.billing.run.service.BillingRunStepSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionReminderPlanningBillingRunStepTest {

    private static final LocalDate TUESDAY = LocalDate.of(2027, 3, 23);
    private static final LocalDate MONDAY = LocalDate.of(2027, 3, 22);

    private final CollectionReminderPlanningStep planningStep = mock(CollectionReminderPlanningStep.class);
    private final OverdueContactListPublicationStep contactListStep = mock(OverdueContactListPublicationStep.class);
    private final PostWithdrawalSummaryPlanningStep summaryStep = mock(PostWithdrawalSummaryPlanningStep.class);
    private final MissedReminderDaysFinder missedDaysFinder = mock(MissedReminderDaysFinder.class);
    private final CollectionReminderRunStore runStore = mock(CollectionReminderRunStore.class);
    private final CollectionReminderPlanningBillingRunStep step = new CollectionReminderPlanningBillingRunStep(planningStep, contactListStep, summaryStep, missedDaysFinder, runStore);
    private final MissedReminderRecovery recovery = MissedReminderRecovery.ofMissedDays(Set.of(MONDAY));

    @BeforeEach
    void findMissedMonday() {
        when(missedDaysFinder.findRecoveryBefore(TUESDAY)).thenReturn(recovery);
    }

    @Test
    @Tag("D-437")
    void aCleanRunCoversTheDayAndTheMissedDaysItRecovered() {
        when(planningStep.planCollectionReminders(TUESDAY, recovery)).thenReturn(new CollectionReminderPlanningResult(1, 1, 0, 0, 0, List.of()));

        step.runStepForBusinessDate(TUESDAY);

        verify(runStore).recordCoveredDay(TUESDAY);
        verify(runStore).recordCoveredDay(MONDAY);
    }

    @Test
    @Tag("D-437")
    void aRunWithStudentFailuresCoversNoDaySoTheNextRunRetriesThem() {
        when(planningStep.planCollectionReminders(TUESDAY, recovery)).thenReturn(new CollectionReminderPlanningResult(2, 1, 0, 0, 1, List.of(new PlanningError(9L, "boom"))));

        step.runStepForBusinessDate(TUESDAY);

        verify(runStore, never()).recordCoveredDay(any());
    }

    @Test
    @Tag("D-436")
    void theStepPlansWithTheRecoveryOfTheMissedBusinessDays() {
        when(planningStep.planCollectionReminders(TUESDAY, recovery)).thenReturn(new CollectionReminderPlanningResult(0, 0, 0, 0, 0, List.of()));

        step.runStepForBusinessDate(TUESDAY);

        verify(planningStep).planCollectionReminders(TUESDAY, recovery);
    }

    @Test
    @Tag("D-451")
    void theOutcomeCountsPlannedAndNothingToSendAsProcessedAndAlreadyPlannedAsSkipped() {
        when(planningStep.planCollectionReminders(TUESDAY, recovery)).thenReturn(new CollectionReminderPlanningResult(10, 4, 3, 2, 1, List.of(new PlanningError(9L, "boom"))));

        BillingRunStepOutcome outcome = step.runStepForBusinessDate(TUESDAY);

        assertThat(outcome.candidates()).isEqualTo(10);
        assertThat(outcome.processed()).isEqualTo(6);
        assertThat(outcome.skipped()).isEqualTo(3);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.items()).singleElement().satisfies(item -> {
            assertThat(item.subjectType()).isEqualTo(BillingRunSubjectType.STUDENT);
            assertThat(item.subjectId()).isEqualTo(9L);
            assertThat(item.status()).isEqualTo(BillingRunItemStatus.FAILED);
            assertThat(item.errorCode()).isEqualTo(AbstractBillingRunStep.SUBJECT_PROCESSING_FAILED);
            assertThat(item.message()).isEqualTo("boom");
        });
    }

    @Test
    @Tag("D-451")
    void aFailingContactListEndsTheStepWithAnErrorButTheRemindersWerePlannedAndTheSummariesStillRun() {
        when(planningStep.planCollectionReminders(TUESDAY, recovery)).thenReturn(new CollectionReminderPlanningResult(2, 2, 0, 0, 0, List.of()));
        doThrow(new IllegalStateException("boom")).when(contactListStep).publishContactListOf(TUESDAY);

        BillingRunStepOutcome outcome = step.runStepForBusinessDate(TUESDAY);

        assertThat(outcome.hasErrors()).isTrue();
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.processed()).isEqualTo(2);
        verify(planningStep).planCollectionReminders(TUESDAY, recovery);
        verify(runStore).recordCoveredDay(TUESDAY);
        verify(summaryStep).planPostWithdrawalSummariesOf(TUESDAY);
    }

    @Test
    @Tag("D-451")
    void aFailingPostWithdrawalSummaryEndsTheStepWithAnErrorAfterThePublishedContactList() {
        when(planningStep.planCollectionReminders(TUESDAY, recovery)).thenReturn(new CollectionReminderPlanningResult(1, 1, 0, 0, 0, List.of()));
        doThrow(new IllegalStateException("boom")).when(summaryStep).planPostWithdrawalSummariesOf(TUESDAY);

        BillingRunStepOutcome outcome = step.runStepForBusinessDate(TUESDAY);

        assertThat(outcome.failed()).isEqualTo(1);
        verify(contactListStep).publishContactListOf(TUESDAY);
    }

    @Test
    @Tag("D-451")
    void theContactListAndTheSummariesAreOnlyPublishedAfterThePlanning() {
        when(planningStep.planCollectionReminders(TUESDAY, recovery)).thenReturn(new CollectionReminderPlanningResult(0, 0, 0, 0, 0, List.of()));

        step.runStepForBusinessDate(TUESDAY);

        var order = inOrder(planningStep, contactListStep, summaryStep);
        order.verify(planningStep).planCollectionReminders(TUESDAY, recovery);
        order.verify(contactListStep).publishContactListOf(TUESDAY);
        order.verify(summaryStep).planPostWithdrawalSummariesOf(TUESDAY);
    }

    @Test
    @Tag("D-449")
    void theRemindersAreTheLastStepOfTheNightAndHaveNoCronOfTheirOwn() {
        BillingRunStepSequence sequence = new BillingRunStepSequence(List.of(step, new CollectionsSnapshotBillingRunStep(null)));

        assertThat(step.step()).isEqualTo(BillingRunStep.COLLECTION_REMINDER_PLANNING);
        assertThat(sequence.orderedSteps()).last().isSameAs(step);
        assertThat(BillingRunStep.values()[BillingRunStep.values().length - 1]).isEqualTo(BillingRunStep.COLLECTION_REMINDER_PLANNING);
        assertThat(Arrays.stream(CollectionReminderPlanningBillingRunStep.class.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Scheduled.class))).isEmpty();
    }
}
