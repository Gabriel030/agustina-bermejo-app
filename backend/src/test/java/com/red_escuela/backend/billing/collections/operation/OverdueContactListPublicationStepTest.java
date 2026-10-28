package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.service.OverdueContactSegment;
import com.red_escuela.backend.billing.collections.service.OverdueContactSegment.ContactListCounts;
import com.red_escuela.backend.billing.events.OverdueContactListUpdatedEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-339")
class OverdueContactListPublicationStepTest {

    private static final LocalDate MARCH_25 = LocalDate.of(2027, 3, 25);

    private final OverdueContactSegment segment = mock(OverdueContactSegment.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final OverdueContactListPublicationStep step = new OverdueContactListPublicationStep(segment, publisher);

    @Test
    void anEmptyListPublishesNothing() {
        when(segment.countContacts(MARCH_25)).thenReturn(new ContactListCounts(0, 0));

        step.publishContactListOf(MARCH_25);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aNonEmptyListPublishesOneEventWithTheCountsOfTheDay() {
        when(segment.countContacts(MARCH_25)).thenReturn(new ContactListCounts(12, 3));

        step.publishContactListOf(MARCH_25);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(published.capture());
        assertThat(published.getValue()).isInstanceOfSatisfying(OverdueContactListUpdatedEvent.class, event -> {
            assertThat(event.businessDate()).isEqualTo(MARCH_25);
            assertThat(event.count()).isEqualTo(12);
            assertThat(event.newCount()).isEqualTo(3);
        });
    }

    @Test
    void theEventOfTheSameDayAlwaysCarriesTheSameIdSoARerunIsNotNotifiedTwice() {
        OverdueContactListUpdatedEvent first = OverdueContactListUpdatedEvent.updatedOn(MARCH_25, 12, 3);
        OverdueContactListUpdatedEvent rerun = OverdueContactListUpdatedEvent.updatedOn(MARCH_25, 14, 5);
        OverdueContactListUpdatedEvent nextDay = OverdueContactListUpdatedEvent.updatedOn(MARCH_25.plusDays(1), 12, 3);

        assertThat(rerun.eventId()).isEqualTo(first.eventId());
        assertThat(nextDay.eventId()).isNotEqualTo(first.eventId());
    }

    @Test
    void thePlanningJobPublishesTheContactListAfterPlanningAndSurvivesItsFailure() {
        CollectionReminderPlanningStep planningStep = mock(CollectionReminderPlanningStep.class);
        OverdueContactListPublicationStep contactListStep = mock(OverdueContactListPublicationStep.class);
        CollectionReminderPlanningResult planned = new CollectionReminderPlanningResult(1, 1, 0, 0, 0, new ArrayList<>());
        when(planningStep.planCollectionReminders(MARCH_25)).thenReturn(planned);
        doThrow(new IllegalStateException("boom")).when(contactListStep).publishContactListOf(eq(MARCH_25));
        CollectionReminderPlanningJob job = new CollectionReminderPlanningJob(planningStep, contactListStep, mock(PostWithdrawalSummaryPlanningStep.class), Clock.systemUTC());

        CollectionReminderPlanningResult result = job.run(MARCH_25);

        assertThat(result).isSameAs(planned);
        verify(contactListStep).publishContactListOf(MARCH_25);
    }
}
