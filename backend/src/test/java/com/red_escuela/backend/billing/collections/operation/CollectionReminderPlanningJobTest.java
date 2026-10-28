package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.MissedReminderRecovery;
import com.red_escuela.backend.billing.collections.repository.CollectionReminderRunStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionReminderPlanningJobTest {

    private static final LocalDate TUESDAY = LocalDate.of(2027, 3, 23);

    private final CollectionReminderPlanningStep planningStep = mock(CollectionReminderPlanningStep.class);
    private final MissedReminderDaysFinder missedDaysFinder = mock(MissedReminderDaysFinder.class);
    private final CollectionReminderRunStore runStore = mock(CollectionReminderRunStore.class);
    private final CollectionReminderPlanningJob job = new CollectionReminderPlanningJob(planningStep, mock(OverdueContactListPublicationStep.class),
            mock(PostWithdrawalSummaryPlanningStep.class), missedDaysFinder, runStore, Clock.systemUTC());
    private final MissedReminderRecovery recovery = MissedReminderRecovery.ofMissedDays(Set.of(LocalDate.of(2027, 3, 22)));

    @BeforeEach
    void findMissedMonday() {
        when(missedDaysFinder.findRecoveryBefore(TUESDAY)).thenReturn(recovery);
    }

    @Test
    @Tag("D-437")
    void aCleanRunCoversTheDayAndTheMissedDaysItRecovered() {
        when(planningStep.planCollectionReminders(TUESDAY, recovery)).thenReturn(new CollectionReminderPlanningResult(1, 1, 0, 0, 0, List.of()));

        job.run(TUESDAY);

        verify(runStore).recordCoveredDay(TUESDAY);
        verify(runStore).recordCoveredDay(LocalDate.of(2027, 3, 22));
    }

    @Test
    @Tag("D-437")
    void aRunWithStudentFailuresCoversNoDaySoTheNextRunRetriesThem() {
        when(planningStep.planCollectionReminders(TUESDAY, recovery)).thenReturn(new CollectionReminderPlanningResult(2, 1, 0, 0, 1, List.of(new PlanningError(9L, "boom"))));

        job.run(TUESDAY);

        verify(runStore, never()).recordCoveredDay(TUESDAY);
        verify(runStore, never()).recordCoveredDay(LocalDate.of(2027, 3, 22));
    }
}
