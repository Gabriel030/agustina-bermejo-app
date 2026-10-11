package com.red_escuela.backend.billing.reporting.domain;

public enum GoalIndicator {
    ON_TIME_PAYMENT(GoalComparator.AT_LEAST),
    OVERDUE_RATIO(GoalComparator.AT_MOST),
    RECEIPT_REVIEW_TIME(GoalComparator.AT_MOST),
    REMINDER_PAYMENT_WITHIN_72H(GoalComparator.AT_LEAST);

    private final GoalComparator comparator;

    GoalIndicator(GoalComparator comparator) {
        this.comparator = comparator;
    }

    public GoalComparator comparator() {
        return comparator;
    }
}
