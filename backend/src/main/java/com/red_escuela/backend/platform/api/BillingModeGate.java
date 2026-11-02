package com.red_escuela.backend.platform.api;

import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import org.springframework.stereotype.Component;

@Component
public class BillingModeGate implements CurrentBillingModePort {

    private final FeatureSnapshotHolder snapshotHolder;

    public BillingModeGate(FeatureSnapshotHolder snapshotHolder) {
        this.snapshotHolder = snapshotHolder;
    }

    @Override
    public BillingMode currentMode() {
        return snapshotHolder.current().billingMode();
    }

    public boolean allows(BillingMode minimumMode) {
        return currentMode().isAtLeast(minimumMode);
    }
}
