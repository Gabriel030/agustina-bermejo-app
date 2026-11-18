package com.red_escuela.backend.notification.broadcast.freezer.domain;

import java.util.List;

public record BroadcastTotals(int sent, int failed, int excluded, int discarded) {

    public static BroadcastTotals from(List<RecipientStatusCount> counts) {
        return new BroadcastTotals(
                totalOf(counts, BroadcastRecipientStatus.SENT),
                totalOf(counts, BroadcastRecipientStatus.FAILED),
                totalOf(counts, BroadcastRecipientStatus.EXCLUDED),
                totalOf(counts, BroadcastRecipientStatus.SKIPPED) + totalOf(counts, BroadcastRecipientStatus.INVALID_NUMBER));
    }

    private static int totalOf(List<RecipientStatusCount> counts, BroadcastRecipientStatus status) {
        return counts.stream().filter(count -> count.status() == status).mapToInt(count -> (int) count.total()).sum();
    }
}
