package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.PaymentMethodCode;

public record MyPaymentMethod(PaymentMethodCode code, boolean available) {
}
