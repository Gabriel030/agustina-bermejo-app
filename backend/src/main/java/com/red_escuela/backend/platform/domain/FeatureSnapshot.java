package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record FeatureSnapshot(Map<FeatureCode, FeatureState> states, Instant loadedAt) {

    public FeatureSnapshot {
        states = Map.copyOf(states);
    }

    public FeatureState stateOf(FeatureCode code) {
        return states.getOrDefault(code, FeatureState.disabled());
    }

    public boolean isEnabled(FeatureCode code) {
        return stateOf(code).enabled();
    }

    public BillingMode billingMode() {
        BillingMode mode = stateOf(FeatureCode.BILLING).mode();
        return mode == null ? BillingMode.OFF : mode;
    }

    public boolean isOlderThan(Duration timeToLive, Instant now) {
        return !loadedAt.plus(timeToLive).isAfter(now);
    }
}
