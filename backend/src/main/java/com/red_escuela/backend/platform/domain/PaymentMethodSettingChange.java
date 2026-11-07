package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.PaymentMethodCode;

public record PaymentMethodSettingChange(PaymentMethodCode code, boolean enabled, Long expectedVersion, String reason) {
}
