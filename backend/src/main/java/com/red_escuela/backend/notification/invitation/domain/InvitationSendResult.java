package com.red_escuela.backend.notification.invitation.domain;

public enum InvitationSendResult {
    SENT,
    FAILED,
    ACTIVATED,
    EXCLUDED,
    DEFERRED,
    NO_QUOTA,
    NOT_SENDABLE
}
