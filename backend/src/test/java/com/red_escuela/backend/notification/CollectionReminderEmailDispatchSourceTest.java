package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.api.CollectionReminderApplicabilityQuery;
import com.red_escuela.backend.billing.api.CollectionReminderEmailsQuery;
import com.red_escuela.backend.billing.api.StoredReminderEmails;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.identity.api.NotificationPreferenceQuery;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.domain.DispatchItem;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.arsBlock;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.digest;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.line;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("D-343")
@Tag("D-372")
class CollectionReminderEmailDispatchSourceTest {
    private static final LocalDate PLANNED_ON = LocalDate.of(2027, 3, 9);
    private static final LocalDate OVERDUE_SINCE = LocalDate.of(2027, 3, 5);
    private static final LocalDate DUE_SOON = LocalDate.of(2027, 3, 15);
    private static final long REMINDER_ID = 77L;
    private static final long PERSON_ID = 5L;

    private CollectionReminderApplicabilityQuery applicability;
    private CollectionReminderEmailsQuery emails;
    private NotificationPreferenceQuery preferences;
    private CollectionReminderEmailDispatchSource source;

    @BeforeEach
    void setUp() {
        applicability = mock(CollectionReminderApplicabilityQuery.class);
        emails = mock(CollectionReminderEmailsQuery.class);
        preferences = mock(NotificationPreferenceQuery.class);
        when(preferences.isPreDueReminderEmailEnabled(PERSON_ID)).thenReturn(true);
        source = new CollectionReminderEmailDispatchSource(applicability, emails, preferences, new CollectionReminderMessageComposer());
    }

    private static PlannedReminderMessage mixedDigest() {
        return digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false,
                arsBlock(line(2, OVERDUE_SINCE, ars(100000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER),
                        line(1, DUE_SOON, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER, PlannedReminderNoticeType.ISSUANCE_NOTICE)));
    }

    private static NotificationDelivery deliveryOf(String type, Long relatedId, String dedupKey) {
        DeliveryEnvelope envelope = new DeliveryEnvelope(dedupKey, DeliveryCategory.COLLECTION_REMINDER, "DAILY_DIGEST:AFTER_DUE_REMINDER", 2, PERSON_ID, type, relatedId, PLANNED_ON);
        return NotificationDelivery.queuedEmail(envelope, DeliveryContent.forEmail("Asunto", "Texto", null), Instant.parse("2027-03-09T08:00:00Z"));
    }

    private static NotificationDelivery reminderDelivery(int ordinal) {
        return deliveryOf("CollectionReminder", REMINDER_ID, "collection-reminder:" + PERSON_ID + ":2027-03-09:" + ordinal);
    }

    private void storedEmails(PlannedReminderMessage... messages) {
        when(emails.findEmailsOfReminder(REMINDER_ID)).thenReturn(Optional.of(new StoredReminderEmails(PLANNED_ON, List.of(messages))));
    }

    @Test
    void everyHighlightedLineIsAnItemWithItsDueDateItsFlagAndItsMostUrgentNotice() {
        storedEmails(mixedDigest());

        List<DispatchItem> items = source.itemsOf(reminderDelivery(0));

        assertThat(items).containsExactly(new DispatchItem("invoice:2", OVERDUE_SINCE, false, 2), new DispatchItem("invoice:1", DUE_SOON, true, 3));
    }

    @Test
    void theOrdinalOfTheDedupKeyPicksTheMessageOfTheReminder() {
        PlannedReminderMessage second = digest(PlannedReminderNoticeType.BEFORE_SUSPENSION_REMINDER, false,
                arsBlock(line(9, OVERDUE_SINCE, ars(1000), false, PlannedReminderNoticeType.BEFORE_SUSPENSION_REMINDER)));
        storedEmails(mixedDigest(), second);

        assertThat(source.itemsOf(reminderDelivery(1))).containsExactly(new DispatchItem("invoice:9", OVERDUE_SINCE, false, 1));
    }

    @Test
    void anOptedOutRecipientHasNoItemForTheLinesThatPrecedeTheirDueDate() {
        when(preferences.isPreDueReminderEmailEnabled(PERSON_ID)).thenReturn(false);
        storedEmails(mixedDigest());

        assertThat(source.itemsOf(reminderDelivery(0))).extracting(DispatchItem::key).containsExactly("invoice:2");
    }

    @Test
    void aDeliveryOfAnotherEntityOrWithoutAnOrdinalHasNoItemsAndIsAlwaysApplicable() {
        storedEmails(mixedDigest());

        assertThat(source.itemsOf(deliveryOf("Invoice", 11L, "other:1"))).isEmpty();
        assertThat(source.itemsOf(deliveryOf("CollectionReminder", REMINDER_ID, "collection-reminder:5:2027-03-09:x"))).isEmpty();
        assertThat(source.itemsOf(reminderDelivery(4))).isEmpty();
        assertThat(source.isStillApplicable(deliveryOf("Invoice", 11L, "other:1"), PLANNED_ON)).isTrue();
        verifyNoInteractions(applicability);
    }

    @Test
    void applicabilityIsAskedToBillingForTheReminderOnTheDispatchDate() {
        when(applicability.isStillApplicable(REMINDER_ID, PLANNED_ON)).thenReturn(false);
        when(applicability.isStillApplicable(REMINDER_ID, PLANNED_ON.plusDays(1))).thenReturn(true);

        assertThat(source.isStillApplicable(reminderDelivery(0), PLANNED_ON)).isFalse();
        assertThat(source.isStillApplicable(reminderDelivery(0), PLANNED_ON.plusDays(1))).isTrue();
    }

    @Test
    void theTrimmedTextDropsTheLineOfTheExpiredItemAndKeepsTheOthers() {
        storedEmails(mixedDigest());

        Optional<DeliveryContent> trimmed = source.contentWithoutItems(reminderDelivery(0), Set.of("invoice:1"));

        assertThat(trimmed).isPresent();
        assertThat(trimmed.get().bodyText()).contains("venció el 05/03/2027").doesNotContain("15/03/2027");
    }

    @Test
    void droppingEveryItemLeavesNoText() {
        storedEmails(mixedDigest());

        assertThat(source.contentWithoutItems(reminderDelivery(0), Set.of("invoice:1", "invoice:2"))).isEmpty();
    }

    @Test
    void applicabilityIsJudgedOnTheInvoicesNoticedByThisMailOnly() {
        storedEmails(mixedDigest());
        when(applicability.isStillApplicable(REMINDER_ID, PLANNED_ON, Set.of(1L, 2L))).thenReturn(false);

        assertThat(source.isStillApplicable(reminderDelivery(0), PLANNED_ON)).isFalse();
        verify(applicability, never()).isStillApplicable(REMINDER_ID, PLANNED_ON);
    }

    @Test
    void aMailMadeOnlyOfPreDueLinesIsSuppressedForARecipientWhoOptedOut() {
        when(preferences.isPreDueReminderEmailEnabled(PERSON_ID)).thenReturn(false);
        storedEmails(digest(PlannedReminderNoticeType.BEFORE_DUE_REMINDER, true, arsBlock(line(1, DUE_SOON, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER))));

        assertThat(source.suppressionReasonOf(reminderDelivery(0))).contains("OPTED_OUT");
    }

    @Test
    void aMixedMailOrARecipientWhoDidNotOptOutIsNotSuppressedByPreference() {
        storedEmails(mixedDigest());
        assertThat(source.suppressionReasonOf(reminderDelivery(0))).isEmpty();
        when(preferences.isPreDueReminderEmailEnabled(PERSON_ID)).thenReturn(false);
        assertThat(source.suppressionReasonOf(reminderDelivery(0))).isEmpty();
        assertThat(source.suppressionReasonOf(deliveryOf("Invoice", 11L, "other:1"))).isEmpty();
    }
}
