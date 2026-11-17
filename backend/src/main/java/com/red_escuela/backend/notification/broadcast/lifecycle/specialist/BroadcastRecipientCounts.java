package com.red_escuela.backend.notification.broadcast.lifecycle.specialist;

import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatusCount;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@Component
public class BroadcastRecipientCounts {

    private final BroadcastRecipientRepository recipientRepository;

    public BroadcastRecipientCounts(BroadcastRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    public Map<Long, Map<BroadcastRecipientStatus, Long>> countByBroadcast(Collection<Long> broadcastIds) {
        Map<Long, Map<BroadcastRecipientStatus, Long>> result = new HashMap<>();
        for (Long id : broadcastIds) {
            Map<BroadcastRecipientStatus, Long> counts = new EnumMap<>(BroadcastRecipientStatus.class);
            for (BroadcastRecipientStatus status : BroadcastRecipientStatus.values()) {
                counts.put(status, 0L);
            }
            result.put(id, counts);
        }
        if (broadcastIds.isEmpty()) {
            return result;
        }
        for (BroadcastRecipientStatusCount row : recipientRepository.countByStatusForBroadcasts(broadcastIds)) {
            result.get(row.broadcastId()).put(row.status(), row.total());
        }
        return result;
    }
}
