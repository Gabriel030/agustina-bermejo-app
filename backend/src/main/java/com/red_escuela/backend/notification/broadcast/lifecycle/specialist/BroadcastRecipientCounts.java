package com.red_escuela.backend.notification.broadcast.lifecycle.specialist;

import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatusCount;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BroadcastRecipientCounts {

    private final BroadcastRecipientRepository recipientRepository;

    public BroadcastRecipientCounts(BroadcastRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    public Map<Long, Map<BroadcastRecipientStatus, Long>> countByBroadcast(Collection<Broadcast> broadcasts) {
        Map<Long, Map<BroadcastRecipientStatus, Long>> result = new HashMap<>();
        List<Long> liveIds = new ArrayList<>();
        for (Broadcast broadcast : broadcasts) {
            Map<BroadcastRecipientStatus, Long> counts = new EnumMap<>(BroadcastRecipientStatus.class);
            for (BroadcastRecipientStatus status : BroadcastRecipientStatus.values()) {
                counts.put(status, 0L);
            }
            result.put(broadcast.getId(), counts);
            if (broadcast.hasFrozenTotals()) {
                counts.put(BroadcastRecipientStatus.SENT, broadcast.getSentTotal().longValue());
                counts.put(BroadcastRecipientStatus.FAILED, broadcast.getFailedTotal().longValue());
                counts.put(BroadcastRecipientStatus.EXCLUDED, broadcast.getExcludedTotal().longValue());
                counts.put(BroadcastRecipientStatus.SKIPPED, broadcast.getDiscardedTotal().longValue());
            } else {
                liveIds.add(broadcast.getId());
            }
        }
        if (liveIds.isEmpty()) {
            return result;
        }
        for (BroadcastRecipientStatusCount row : recipientRepository.countByStatusForBroadcasts(liveIds)) {
            result.get(row.broadcastId()).put(row.status(), row.total());
        }
        return result;
    }
}
