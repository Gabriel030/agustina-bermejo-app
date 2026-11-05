package com.red_escuela.backend.billing.run.domain;

import java.util.Objects;

public record BillingRunItemRecord(BillingRunSubjectType subjectType, Long subjectId, BillingRunItemStatus status, String decisionCode, String errorCode, String message) {

    public BillingRunItemRecord {
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(status, "status");
    }

    public static BillingRunItemRecord skippedSubject(BillingRunSubjectType subjectType, Long subjectId, String decisionCode, String message) {
        return new BillingRunItemRecord(subjectType, subjectId, BillingRunItemStatus.SKIPPED, decisionCode, null, message);
    }

    public static BillingRunItemRecord failedSubject(BillingRunSubjectType subjectType, Long subjectId, String errorCode, String message) {
        return new BillingRunItemRecord(subjectType, subjectId, BillingRunItemStatus.FAILED, null, errorCode, message);
    }
}
