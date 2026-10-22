package com.red_escuela.backend.notification.delivery.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EmailDispatchPlanner {

    private static final Comparator<PlannedDispatch> MOST_URGENT_FIRST =
            Comparator.comparingInt(PlannedDispatch::priority).thenComparingLong(PlannedDispatch::deliveryId);

    public EmailDispatchPlan planFor(List<DispatchCandidate> candidates, LocalDate dispatchDate, LocalDate nextBusinessDay, int remainingCapacity) {
        Objects.requireNonNull(dispatchDate, "dispatchDate");
        Objects.requireNonNull(nextBusinessDay, "nextBusinessDay");
        if (!nextBusinessDay.isAfter(dispatchDate)) {
            throw new IllegalArgumentException("The next business day must be after the dispatch date");
        }
        if (remainingCapacity < 0) {
            throw new IllegalArgumentException("The remaining capacity cannot be negative");
        }
        requireDistinctDeliveries(candidates);
        List<PlannedDispatch> timely = new ArrayList<>();
        List<DispatchDiscard> discards = new ArrayList<>();
        for (DispatchCandidate candidate : candidates) {
            List<DispatchItem> kept = candidate.items().stream().filter(item -> !item.isNoLongerTimelyOn(dispatchDate)).toList();
            if (kept.isEmpty()) {
                discards.add(new DispatchDiscard(candidate.deliveryId(), DeliveryStatusReason.NO_LONGER_TIMELY));
                continue;
            }
            List<DispatchItem> dropped = candidate.items().stream().filter(item -> item.isNoLongerTimelyOn(dispatchDate)).toList();
            timely.add(dispatchOf(candidate.deliveryId(), dispatchDate, kept, dropped));
        }
        List<PlannedDispatch> ordered = timely.stream().sorted(MOST_URGENT_FIRST).toList();
        int cutoff = Math.min(remainingCapacity, ordered.size());
        List<PlannedDispatch> deferred = ordered.subList(cutoff, ordered.size()).stream()
                .map(dispatch -> dispatchOf(dispatch.deliveryId(), nextBusinessDay, dispatch.keptItems(), dispatch.droppedItems())).toList();
        return new EmailDispatchPlan(ordered.subList(0, cutoff), deferred, discards);
    }

    private static PlannedDispatch dispatchOf(long deliveryId, LocalDate scheduledFor, List<DispatchItem> kept, List<DispatchItem> dropped) {
        int priority = kept.stream().mapToInt(DispatchItem::priority).min().orElseThrow();
        boolean precedesDueDate = kept.stream().allMatch(item -> item.precedesDueDateWhenSentOn(scheduledFor));
        return new PlannedDispatch(deliveryId, scheduledFor, priority, kept, dropped, precedesDueDate);
    }

    private static void requireDistinctDeliveries(List<DispatchCandidate> candidates) {
        Set<Long> seen = new HashSet<>();
        for (DispatchCandidate candidate : candidates) {
            if (!seen.add(candidate.deliveryId())) {
                throw new IllegalArgumentException("A delivery appears twice in the dispatch candidates");
            }
        }
    }
}
