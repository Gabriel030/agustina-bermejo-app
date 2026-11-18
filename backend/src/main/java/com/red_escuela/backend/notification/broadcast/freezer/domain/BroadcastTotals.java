package com.red_escuela.backend.notification.broadcast.freezer.domain;

import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record BroadcastTotals(int sent, int failed, int excluded, int discarded, Map<ExclusionReason, Integer> excludedByReason) {

    public static BroadcastTotals from(List<RecipientStatusCount> counts, List<ExclusionReasonCount> reasonCounts) {
        Map<ExclusionReason, Integer> byReason = new EnumMap<>(ExclusionReason.class);
        reasonCounts.forEach(count -> byReason.merge(count.reason(), (int) count.total(), Integer::sum));
        return new BroadcastTotals(
                totalOf(counts, BroadcastRecipientStatus.SENT),
                totalOf(counts, BroadcastRecipientStatus.FAILED),
                totalOf(counts, BroadcastRecipientStatus.EXCLUDED),
                totalOf(counts, BroadcastRecipientStatus.SKIPPED) + totalOf(counts, BroadcastRecipientStatus.INVALID_NUMBER),
                byReason);
    }

    public String excludedByReasonAsJson() {
        return excludedByReason.entrySet().stream()
                .map(entry -> "\"" + entry.getKey().name() + "\":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static int totalOf(List<RecipientStatusCount> counts, BroadcastRecipientStatus status) {
        return counts.stream().filter(count -> count.status() == status).mapToInt(count -> (int) count.total()).sum();
    }
}
