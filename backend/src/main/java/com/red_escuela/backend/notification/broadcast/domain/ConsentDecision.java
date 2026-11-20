package com.red_escuela.backend.notification.broadcast.domain;

public record ConsentDecision(boolean allowed, DenialReason denialReason) {

    private static final ConsentDecision ALLOWED = new ConsentDecision(true, null);

    public static ConsentDecision permitted() {
        return ALLOWED;
    }

    public static ConsentDecision denied(DenialReason reason) {
        return new ConsentDecision(false, reason);
    }
}
