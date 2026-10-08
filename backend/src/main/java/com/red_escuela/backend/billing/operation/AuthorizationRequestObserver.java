package com.red_escuela.backend.billing.operation;

public interface AuthorizationRequestObserver {

    default void afterOpened(Long requestId, BillingOperationType type, Object context) {
    }

    default void afterRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
    }

    default void afterWithdrawn(Long requestId, BillingOperationType type, Long requesterId) {
    }

    default void afterExpired(Long requestId, BillingOperationType type) {
    }

    default void afterResumeRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
    }
}
