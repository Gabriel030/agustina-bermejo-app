package com.red_escuela.backend.platform.api;

import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.domain.InstituteSettingsSnapshot;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.platform.model.PaymentMethodCode;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class PaymentMethodAvailability {

    private final FeatureSnapshotHolder featureSnapshotHolder;
    private final InstituteSettingsSnapshotHolder instituteSnapshotHolder;

    public PaymentMethodAvailability(FeatureSnapshotHolder featureSnapshotHolder, InstituteSettingsSnapshotHolder instituteSnapshotHolder) {
        this.featureSnapshotHolder = featureSnapshotHolder;
        this.instituteSnapshotHolder = instituteSnapshotHolder;
    }

    public boolean isAvailable(PaymentMethodCode code) {
        return statusOf(code, featureSnapshotHolder.current(), instituteSnapshotHolder.current()).effective();
    }

    public List<PaymentMethodStatus> statusOfAll() {
        FeatureSnapshot features = featureSnapshotHolder.current();
        InstituteSettingsSnapshot institute = instituteSnapshotHolder.current();
        return Arrays.stream(PaymentMethodCode.values()).map(code -> statusOf(code, features, institute)).toList();
    }

    private static PaymentMethodStatus statusOf(PaymentMethodCode code, FeatureSnapshot features, InstituteSettingsSnapshot institute) {
        boolean platformEnabled = features.isEnabled(code.featureCode());
        boolean instituteEnabled = institute.isEnabled(code.settingCode());
        boolean effective = features.billingMode() == BillingMode.FULL && platformEnabled && instituteEnabled;
        return new PaymentMethodStatus(code, platformEnabled, instituteEnabled, effective);
    }

    public record PaymentMethodStatus(PaymentMethodCode code, boolean platformEnabled, boolean instituteEnabled, boolean effective) {
    }
}
