package com.red_escuela.backend.notification.broadcast.lifecycle.specialist;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastDispatchSettings;
import com.red_escuela.backend.notification.delivery.domain.DailyEmailSentCounts;
import com.red_escuela.backend.notification.delivery.specialist.SharedDailyEmailQuota;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

@Component
public class BroadcastCompletionEstimator {

    private final SharedDailyEmailQuota sharedQuota;
    private final BroadcastDispatchSettings settings;
    private final Clock clock;

    public BroadcastCompletionEstimator(SharedDailyEmailQuota sharedQuota, BroadcastDispatchSettings settings, Clock clock) {
        this.sharedQuota = sharedQuota;
        this.settings = settings;
        this.clock = clock;
    }

    public LocalDate estimate(BroadcastStatus status, Map<BroadcastRecipientStatus, Long> counts) {
        if (status != BroadcastStatus.CONFIRMED && status != BroadcastStatus.SENDING) {
            return null;
        }
        long pending = counts.getOrDefault(BroadcastRecipientStatus.PENDING, 0L) + counts.getOrDefault(BroadcastRecipientStatus.QUEUED, 0L);
        DailyEmailSentCounts sentToday = sharedQuota.countSentToday();
        return settings.quota().completionDate(pending, LocalDate.now(clock), sentToday.transactionalSent(), sentToday.broadcastSent());
    }
}
