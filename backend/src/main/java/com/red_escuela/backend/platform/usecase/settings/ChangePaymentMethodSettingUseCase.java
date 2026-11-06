package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.InstituteSettings.PaymentMethodSetting;
import com.red_escuela.backend.platform.model.PaymentMethodCode;

public interface ChangePaymentMethodSettingUseCase {
    PaymentMethodSetting change(PaymentMethodCode code, boolean enabled, Long expectedVersion, String reason);
}
