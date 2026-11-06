package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.api.PaymentMethodAvailability.PaymentMethodStatus;
import com.red_escuela.backend.platform.model.PaymentMethodCode;

public record MyPaymentMethodResponse(PaymentMethodCode code, boolean available) {

    public static MyPaymentMethodResponse of(PaymentMethodStatus status) {
        return new MyPaymentMethodResponse(status.code(), status.effective());
    }
}
