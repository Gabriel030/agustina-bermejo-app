package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.domain.InstituteSettings;
import com.red_escuela.backend.platform.domain.InstituteSettings.BillingNotificationsSetting;
import com.red_escuela.backend.platform.domain.InstituteSettings.PaymentMethodSetting;
import com.red_escuela.backend.platform.entity.InstituteSetting;
import com.red_escuela.backend.platform.exception.InstituteSettingNotProvisionedException;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.platform.model.PaymentMethodCode;
import com.red_escuela.backend.platform.repository.InstituteSettingRepository;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class InstituteSettingsReader {

    private final InstituteSettingRepository settingRepository;
    private final FeatureSnapshotHolder featureSnapshotHolder;

    public InstituteSettingsReader(InstituteSettingRepository settingRepository, FeatureSnapshotHolder featureSnapshotHolder) {
        this.settingRepository = settingRepository;
        this.featureSnapshotHolder = featureSnapshotHolder;
    }

    @Transactional(readOnly = true)
    public InstituteSettings readAll() {
        FeatureSnapshot features = featureSnapshotHolder.current();
        List<PaymentMethodSetting> methods = Arrays.stream(PaymentMethodCode.values()).map(code -> paymentMethodOf(code, features)).toList();
        return new InstituteSettings(methods, billingNotificationsOf(features));
    }

    @Transactional(readOnly = true)
    public PaymentMethodSetting readPaymentMethod(PaymentMethodCode code) {
        return paymentMethodOf(code, featureSnapshotHolder.current());
    }

    @Transactional(readOnly = true)
    public BillingNotificationsSetting readBillingNotifications() {
        return billingNotificationsOf(featureSnapshotHolder.current());
    }

    private PaymentMethodSetting paymentMethodOf(PaymentMethodCode code, FeatureSnapshot features) {
        InstituteSetting setting = settingOf(code.settingCode());
        boolean platformEnabled = features.isEnabled(code.featureCode());
        boolean effective = features.billingMode() == BillingMode.FULL && platformEnabled && setting.isEnabled();
        return new PaymentMethodSetting(code, platformEnabled, setting.isEnabled(), effective, setting.getVersion());
    }

    private BillingNotificationsSetting billingNotificationsOf(FeatureSnapshot features) {
        InstituteSetting setting = settingOf(InstituteSettingCode.BILLING_NOTIFICATIONS);
        boolean platformEnabled = features.isEnabled(FeatureCode.BILLING_NOTIFICATIONS);
        boolean effective = features.billingMode() == BillingMode.FULL && platformEnabled && setting.isEnabled();
        return new BillingNotificationsSetting(platformEnabled, setting.isEnabled(), effective, setting.getVersion());
    }

    private InstituteSetting settingOf(InstituteSettingCode code) {
        return settingRepository.findById(code).orElseThrow(() -> new InstituteSettingNotProvisionedException(code));
    }
}
