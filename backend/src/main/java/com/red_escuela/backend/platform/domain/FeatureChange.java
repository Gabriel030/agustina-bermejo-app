package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;

public record FeatureChange(
        FeatureCode code,
        Boolean enabled,
        BillingMode mode,
        Long expectedVersion,
        String reason,
        BillingModeChangeClearance clearance,
        String previewConfirmationToken) {

    public FeatureChange(FeatureCode code, Boolean enabled, BillingMode mode, Long expectedVersion, String reason, BillingModeChangeClearance clearance) {
        this(code, enabled, mode, expectedVersion, reason, clearance, null);
    }
}
