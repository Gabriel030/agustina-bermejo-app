package com.red_escuela.backend.platform.api;

import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.model.BillingMode;
import org.springframework.stereotype.Component;

@Component
public class BillingModeGate {

    private final FeatureSnapshotHolder snapshotHolder;

    public BillingModeGate(FeatureSnapshotHolder snapshotHolder) {
        this.snapshotHolder = snapshotHolder;
    }

    public BillingMode currentMode() {
        return snapshotHolder.current().billingMode();
    }

    public boolean allows(BillingMode minimumMode) {
        return currentMode().isAtLeast(minimumMode);
    }
}
