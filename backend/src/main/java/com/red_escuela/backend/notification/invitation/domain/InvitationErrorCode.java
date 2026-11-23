package com.red_escuela.backend.notification.invitation.domain;

public enum InvitationErrorCode {
    PROVIDER_REJECTED,
    PROVIDER_UNAVAILABLE,
    CIRCUIT_OPEN,
    BULKHEAD_FULL,
    TOKEN_ISSUE_FAILED,
    UNKNOWN
}
