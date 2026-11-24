package com.red_escuela.backend.notification.invitation.domain;

public enum InvitationExclusionReason {
    NO_EMAIL,
    ALREADY_ACTIVE,
    NOT_ELIGIBLE,
    IN_OTHER_WAVE,
    WAVE_CANCELLED,
    SHARED_EMAIL
}
