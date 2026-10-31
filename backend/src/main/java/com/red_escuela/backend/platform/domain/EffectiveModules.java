package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;

public record EffectiveModules(BillingMode billingMode, FeatureSnapshot snapshot) {

    public boolean billingEnabled() {
        return billingMode.isEnabled();
    }

    public boolean analyticsEnabled() {
        return snapshot.isEnabled(FeatureCode.ANALYTICS);
    }

    public boolean availableWithBilling(FeatureCode code) {
        return billingEnabled() && snapshot.isEnabled(code);
    }
}
