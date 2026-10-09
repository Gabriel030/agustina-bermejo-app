package com.red_escuela.backend.billing.usecase.latepaymentsettings;

import com.red_escuela.backend.billing.dto.CreateLatePaymentSettingsRequest;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersion;

public interface CreateLatePaymentSettingsUseCase {
    ConfigurationVersion create(CreateLatePaymentSettingsRequest request);
}
