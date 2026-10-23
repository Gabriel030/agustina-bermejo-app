package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.api.CollectionReminderApplicabilityQuery;
import com.red_escuela.backend.billing.api.CollectionReminderEmailsQuery;
import com.red_escuela.backend.billing.api.StoredReminderEmails;
import com.red_escuela.backend.billing.events.PlannedReminderFormat;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNotice;
import com.red_escuela.backend.identity.api.NotificationPreferenceQuery;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatusReason;
import com.red_escuela.backend.notification.delivery.domain.DispatchItem;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.service.EmailDispatchSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CollectionReminderEmailDispatchSource implements EmailDispatchSource {

    static final String ITEM_KEY_PREFIX = "invoice:";

    private final CollectionReminderApplicabilityQuery applicabilityQuery;
    private final CollectionReminderEmailsQuery emailsQuery;
    private final NotificationPreferenceQuery preferenceQuery;
    private final CollectionReminderMessageComposer composer;

    public CollectionReminderEmailDispatchSource(CollectionReminderApplicabilityQuery applicabilityQuery, CollectionReminderEmailsQuery emailsQuery,
                                                 NotificationPreferenceQuery preferenceQuery, CollectionReminderMessageComposer composer) {
        this.applicabilityQuery = applicabilityQuery;
        this.emailsQuery = emailsQuery;
        this.preferenceQuery = preferenceQuery;
        this.composer = composer;
    }

    @Override
    public boolean isStillApplicable(NotificationDelivery delivery, LocalDate date) {
        if (!isCollectionReminder(delivery)) {
            return true;
        }
        return storedMessageOf(delivery)
                .map(stored -> applicabilityQuery.isStillApplicable(delivery.getRelatedEntityId(), date, highlightedInvoiceIdsOf(stored.message())))
                .orElseGet(() -> applicabilityQuery.isStillApplicable(delivery.getRelatedEntityId(), date));
    }

    @Override
    public Optional<String> suppressionReasonOf(NotificationDelivery delivery) {
        if (!isCollectionReminder(delivery) || preferenceQuery.isPreDueReminderEmailEnabled(delivery.getRecipientPersonId())) {
            return Optional.empty();
        }
        return emailsQuery.findEmailsOfReminder(delivery.getRelatedEntityId())
                .map(StoredReminderEmails::emailMessages)
                .filter(messages -> ordinalOf(delivery.getDedupKey()) >= 0 && ordinalOf(delivery.getDedupKey()) < messages.size())
                .filter(messages -> messages.get(ordinalOf(delivery.getDedupKey())).withoutLinesPrecedingTheirDueDate().isEmpty())
                .map(messages -> DeliveryStatusReason.OPTED_OUT);
    }

    private static Set<Long> highlightedInvoiceIdsOf(PlannedReminderMessage message) {
        return message.lines().stream().filter(PlannedReminderLine::isHighlighted).map(PlannedReminderLine::invoiceId).collect(Collectors.toSet());
    }

    @Override
    public List<DispatchItem> itemsOf(NotificationDelivery delivery) {
        return storedMessageOf(delivery).map(stored -> itemsOf(stored.message())).orElse(List.of());
    }

    @Override
    public Optional<DeliveryContent> contentWithoutItems(NotificationDelivery delivery, Set<String> itemKeys) {
        Set<Long> invoiceIds = itemKeys.stream().filter(key -> key.startsWith(ITEM_KEY_PREFIX)).map(key -> Long.valueOf(key.substring(ITEM_KEY_PREFIX.length())))
                .collect(Collectors.toSet());
        return storedMessageOf(delivery).flatMap(stored -> stored.message().withoutLinesOfInvoices(invoiceIds)
                .map(remaining -> composer.composeEmail(remaining, stored.businessDate())));
    }

    private List<DispatchItem> itemsOf(PlannedReminderMessage message) {
        if (message.format() == PlannedReminderFormat.DEBT_SUMMARY) {
            return List.of();
        }
        return message.lines().stream().filter(PlannedReminderLine::isHighlighted).map(line -> new DispatchItem(ITEM_KEY_PREFIX + line.invoiceId(), line.dueDate(),
                line.precedesDueDate(), line.notices().stream().mapToInt(CollectionReminderEmailDispatchSource::priorityOf).min().orElseThrow())).toList();
    }

    private static int priorityOf(PlannedReminderNotice notice) {
        return notice.noticeType().dispatchPriority();
    }

    private Optional<StoredMessage> storedMessageOf(NotificationDelivery delivery) {
        if (!isCollectionReminder(delivery)) {
            return Optional.empty();
        }
        int ordinal = ordinalOf(delivery.getDedupKey());
        return emailsQuery.findEmailsOfReminder(delivery.getRelatedEntityId())
                .filter(stored -> ordinal >= 0 && ordinal < stored.emailMessages().size())
                .map(stored -> new StoredMessage(selectedForRecipient(stored, ordinal, delivery.getRecipientPersonId()), stored.businessDate()));
    }

    private PlannedReminderMessage selectedForRecipient(StoredReminderEmails stored, int ordinal, Long recipientPersonId) {
        PlannedReminderMessage message = stored.emailMessages().get(ordinal);
        if (preferenceQuery.isPreDueReminderEmailEnabled(recipientPersonId)) {
            return message;
        }
        return message.withoutLinesPrecedingTheirDueDate().orElse(message);
    }

    private static boolean isCollectionReminder(NotificationDelivery delivery) {
        return CollectionReminderNotificationListener.RELATED_ENTITY_TYPE.equals(delivery.getRelatedEntityType()) && delivery.getRelatedEntityId() != null;
    }

    private static int ordinalOf(String dedupKey) {
        int lastSeparator = dedupKey.lastIndexOf(':');
        try {
            return Integer.parseInt(dedupKey.substring(lastSeparator + 1));
        } catch (NumberFormatException notAnOrdinal) {
            return -1;
        }
    }

    private record StoredMessage(PlannedReminderMessage message, LocalDate businessDate) {
    }
}
