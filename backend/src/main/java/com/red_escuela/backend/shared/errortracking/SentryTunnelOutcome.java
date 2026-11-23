package com.red_escuela.backend.shared.errortracking;

public enum SentryTunnelOutcome {
    FORWARDED,
    DISABLED,
    PAYLOAD_TOO_LARGE,
    REJECTED_ENVELOPE,
    UPSTREAM_REJECTED,
    UPSTREAM_UNAVAILABLE
}
