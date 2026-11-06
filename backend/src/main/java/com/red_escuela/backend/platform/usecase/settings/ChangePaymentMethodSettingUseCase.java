package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.InstituteSettings.PaymentMethodSetting;
import com.red_escuela.backend.platform.domain.PaymentMethodSettingChange;

public interface ChangePaymentMethodSettingUseCase {
    PaymentMethodSetting change(PaymentMethodSettingChange change);
}
