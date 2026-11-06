package com.red_escuela.backend.platform.api;

import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.stereotype.Component;

@Component
public class BillingNotificationsPolicy {

    private final FeatureSnapshotHolder featureSnapshotHolder;
    private final InstituteSettingsSnapshotHolder instituteSnapshotHolder;

    public BillingNotificationsPolicy(FeatureSnapshotHolder featureSnapshotHolder, InstituteSettingsSnapshotHolder instituteSnapshotHolder) {
        this.featureSnapshotHolder = featureSnapshotHolder;
        this.instituteSnapshotHolder = instituteSnapshotHolder;
    }

    public boolean areBillingNotificationsEffective() {
        FeatureSnapshot features = featureSnapshotHolder.current();
        return features.billingMode() == BillingMode.FULL
                && features.isEnabled(FeatureCode.BILLING_NOTIFICATIONS)
                && instituteSnapshotHolder.current().isEnabled(InstituteSettingCode.BILLING_NOTIFICATIONS);
    }
}
