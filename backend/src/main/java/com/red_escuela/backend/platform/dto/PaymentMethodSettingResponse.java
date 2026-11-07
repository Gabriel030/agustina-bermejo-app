package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.InstituteSettings.PaymentMethodSetting;
import com.red_escuela.backend.platform.model.PaymentMethodCode;

public record PaymentMethodSettingResponse(PaymentMethodCode code, boolean platformEnabled, boolean enabled, boolean effective, long version) {

    public static PaymentMethodSettingResponse of(PaymentMethodSetting setting) {
        return new PaymentMethodSettingResponse(setting.code(), setting.platformEnabled(), setting.enabled(), setting.effective(), setting.version());
    }
}
