package com.red_escuela.backend.billing.usecase.latepaymentsettings;

import com.red_escuela.backend.billing.entity.LatePaymentSettings;

public interface FindCurrentLatePaymentSettingsUseCase {
    LatePaymentSettings findCurrent();
}
