package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.api.PaymentMethodAvailability.PaymentMethodStatus;

import java.util.List;

public interface GetMyPaymentMethodsUseCase {
    List<PaymentMethodStatus> read();
}
