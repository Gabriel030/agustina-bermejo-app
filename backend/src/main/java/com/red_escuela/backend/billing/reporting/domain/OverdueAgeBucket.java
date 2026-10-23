package com.red_escuela.backend.billing.reporting.domain;

public enum OverdueAgeBucket {
    FROM_1_TO_30(30),
    FROM_31_TO_60(60),
    FROM_61_TO_90(90),
    OVER_90(Long.MAX_VALUE);

    private final long lastDay;

    OverdueAgeBucket(long lastDay) {
        this.lastDay = lastDay;
    }

    public static OverdueAgeBucket forDaysOverdue(long daysOverdue) {
        if (daysOverdue < 1) {
            throw new IllegalArgumentException("An invoice is overdue from the day after its due date: " + daysOverdue);
        }
        for (OverdueAgeBucket bucket : values()) {
            if (daysOverdue <= bucket.lastDay) {
                return bucket;
            }
        }
        throw new IllegalStateException("Unreachable: the last bucket has no upper bound");
    }
}
