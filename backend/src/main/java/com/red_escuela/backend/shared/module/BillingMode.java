package com.red_escuela.backend.shared.module;

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
