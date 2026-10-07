package com.red_escuela.backend.billing.usecase.latepaymentsettings;

import com.red_escuela.backend.billing.dto.CreateLatePaymentSettingsRequest;
import com.red_escuela.backend.billing.entity.LatePaymentSettings;

public interface CreateLatePaymentSettingsUseCase {
    LatePaymentSettings create(CreateLatePaymentSettingsRequest request);
}
