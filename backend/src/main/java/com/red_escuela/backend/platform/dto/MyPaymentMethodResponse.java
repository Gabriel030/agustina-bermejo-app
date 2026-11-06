package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.MyPaymentMethod;
import com.red_escuela.backend.platform.model.PaymentMethodCode;

public record MyPaymentMethodResponse(PaymentMethodCode code, boolean available) {

    public static MyPaymentMethodResponse of(MyPaymentMethod method) {
        return new MyPaymentMethodResponse(method.code(), method.available());
    }
}
