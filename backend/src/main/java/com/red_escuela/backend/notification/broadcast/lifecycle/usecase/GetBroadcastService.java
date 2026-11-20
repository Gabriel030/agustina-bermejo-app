package com.red_escuela.backend.notification.broadcast.lifecycle.usecase;

import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastNotFoundException;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.lifecycle.dto.BroadcastDetailResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastCompletionEstimator;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastRecipientCounts;
import com.red_escuela.backend.shared.UseCase;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@UseCase
public class GetBroadcastService implements GetBroadcastUseCase {

    private final BroadcastRepository broadcastRepository;
    private final BroadcastRecipientCounts recipientCounts;
    private final BroadcastCompletionEstimator completionEstimator;
    private final PersonNameFinder personNameFinder;

    public GetBroadcastService(BroadcastRepository broadcastRepository, BroadcastRecipientCounts recipientCounts,
                               BroadcastCompletionEstimator completionEstimator, PersonNameFinder personNameFinder) {
        this.broadcastRepository = broadcastRepository;
        this.recipientCounts = recipientCounts;
        this.completionEstimator = completionEstimator;
        this.personNameFinder = personNameFinder;
    }

    @Override
    public BroadcastDetailResponse getBroadcast(Long broadcastId) {
        var broadcast = broadcastRepository.findById(broadcastId).orElseThrow(() -> new BroadcastNotFoundException(broadcastId));
        Map<BroadcastRecipientStatus, Long> counts = recipientCounts.countByBroadcast(java.util.List.of(broadcast)).get(broadcastId);
        Map<Long, String> names = personNameFinder.findFullNamesByPersonId(
                Stream.of(broadcast.getCreatedByPersonId(), broadcast.getConfirmedByPersonId()).filter(Objects::nonNull).toList());
        return BroadcastDetailResponse.from(broadcast, counts, names, completionEstimator.estimate(broadcast.getStatus(), counts));
    }
}
