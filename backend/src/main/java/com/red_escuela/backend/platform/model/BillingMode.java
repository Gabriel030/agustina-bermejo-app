package com.red_escuela.backend.platform.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("model")
public enum BillingMode {
    OFF,
    MIRROR,
    FULL;

    public boolean isAtLeast(BillingMode minimum) {
        return compareTo(minimum) >= 0;
    }

    public boolean isEnabled() {
        return this != OFF;
    }
}
