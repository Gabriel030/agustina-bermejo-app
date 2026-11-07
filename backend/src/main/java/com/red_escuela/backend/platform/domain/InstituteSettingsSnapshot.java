package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.InstituteSettingCode;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record InstituteSettingsSnapshot(Map<InstituteSettingCode, InstituteSettingState> states, Instant loadedAt) {

    public InstituteSettingsSnapshot {
        states = Map.copyOf(states);
    }

    public InstituteSettingState stateOf(InstituteSettingCode code) {
        return states.getOrDefault(code, new InstituteSettingState(false, 0));
    }

    public boolean isEnabled(InstituteSettingCode code) {
        return stateOf(code).enabled();
    }

    public boolean isOlderThan(Duration timeToLive, Instant now) {
        return !loadedAt.plus(timeToLive).isAfter(now);
    }
}
