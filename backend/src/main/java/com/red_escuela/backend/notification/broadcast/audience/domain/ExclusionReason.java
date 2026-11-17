package com.red_escuela.backend.notification.broadcast.audience.domain;

public enum ExclusionReason {
    NO_VERIFIED_EMAIL,
    OPTED_OUT,
    NO_CONSENT,
    NO_VALID_PHONE,
    NO_CHANNEL_ADDRESS,
    MISSING_TEMPLATE_VARIABLE
}
