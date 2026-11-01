package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.shared.module.BillingMode;

public record FeatureState(boolean enabled, BillingMode mode, long version) {

    public static FeatureState disabled() {
        return new FeatureState(false, null, 0);
    }

    public static FeatureState billing(BillingMode mode, long version) {
        return new FeatureState(mode.isEnabled(), mode, version);
    }
}
