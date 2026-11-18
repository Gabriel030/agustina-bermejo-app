package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastNotFoundException;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.domain.RecipientStatusCount;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastDetailResponse;
import com.red_escuela.backend.shared.UseCase;

import java.util.EnumMap;
import java.util.Map;

@UseCase
public class GetBroadcastService implements GetBroadcastUseCase {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;

    public GetBroadcastService(BroadcastRepository broadcastRepository, BroadcastRecipientRepository recipientRepository) {
        this.broadcastRepository = broadcastRepository;
        this.recipientRepository = recipientRepository;
    }

    @Override
    public BroadcastDetailResponse getBroadcast(Long broadcastId) {
        var broadcast = broadcastRepository.findById(broadcastId).orElseThrow(() -> new BroadcastNotFoundException(broadcastId));
        Map<BroadcastRecipientStatus, Long> counts = new EnumMap<>(BroadcastRecipientStatus.class);
        for (BroadcastRecipientStatus status : BroadcastRecipientStatus.values()) {
            counts.put(status, 0L);
        }
        if (broadcast.hasFrozenTotals()) {
            counts.put(BroadcastRecipientStatus.SENT, broadcast.getSentTotal().longValue());
            counts.put(BroadcastRecipientStatus.FAILED, broadcast.getFailedTotal().longValue());
            counts.put(BroadcastRecipientStatus.EXCLUDED, broadcast.getExcludedTotal().longValue());
            counts.put(BroadcastRecipientStatus.SKIPPED, broadcast.getDiscardedTotal().longValue());
        } else {
            for (RecipientStatusCount count : recipientRepository.countByStatus(broadcastId)) {
                counts.put(count.status(), count.total());
            }
        }
        return BroadcastDetailResponse.from(broadcast, counts);
    }
}
