package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.shared.retention.RetentionBatchPurger;
import com.red_escuela.backend.shared.retention.RetentionSettings;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class NotificationDataRetentionJob {

    private static final String TERMINAL_DELIVERY_STATUSES = Stream.of(DeliveryStatus.SENT, DeliveryStatus.SENT_SIMULATED,
                    DeliveryStatus.SUPPRESSED, DeliveryStatus.DISCARDED, DeliveryStatus.FAILED)
            .map(status -> "'" + status.name() + "'")
            .collect(Collectors.joining(", "));
    private static final String COLLECTION_REMINDER = "'" + DeliveryCategory.COLLECTION_REMINDER.name() + "'";
    private static final String OTHER_DELIVERIES = "scheduled_for < :cutoff and category <> " + COLLECTION_REMINDER
            + " and status in (" + TERMINAL_DELIVERY_STATUSES + ")";
    private static final String COLLECTION_REMINDER_DELIVERIES = "scheduled_for < :cutoff and category = " + COLLECTION_REMINDER
            + " and status in (" + TERMINAL_DELIVERY_STATUSES + ")";
    private static final String NOTIFICATIONS_WITHOUT_PENDING_PROCEDURE = "created_at < :cutoff and (read = true or related_entity_id is null)";

    private final RetentionBatchPurger purger;
    private final RetentionSettings settings;
    private final Clock clock;

    public NotificationDataRetentionJob(RetentionBatchPurger purger, RetentionSettings settings, Clock clock) {
        this.purger = purger;
        this.settings = settings;
        this.clock = clock;
    }

    @Scheduled(cron = "0 50 3 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "NotificationDataRetentionJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        deleteExpiredNotificationData();
    }

    public int deleteExpiredNotificationData() {
        if (!settings.enabled()) {
            return 0;
        }
        LocalDate today = LocalDate.now(clock);
        int deleted = purger.purge("notification_delivery", "notification_delivery", OTHER_DELIVERIES,
                new MapSqlParameterSource("cutoff", Date.valueOf(today.minusMonths(settings.notificationDeliveryMonths()))));
        deleted += purger.purge("notification_delivery_collection_reminder", "notification_delivery", COLLECTION_REMINDER_DELIVERIES,
                new MapSqlParameterSource("cutoff", Date.valueOf(today.minusMonths(settings.collectionReminderDeliveryMonths()))));
        deleted += purger.purge("notification", "notification", NOTIFICATIONS_WITHOUT_PENDING_PROCEDURE,
                new MapSqlParameterSource("cutoff", LocalDateTime.now(clock).minusMonths(settings.notificationMonths())));
        return deleted;
    }
}
